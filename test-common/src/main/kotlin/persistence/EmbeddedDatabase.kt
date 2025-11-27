package no.nav.su.se.bakover.test.persistence

import io.zonky.test.db.postgres.embedded.DatabasePreparer
import io.zonky.test.db.postgres.embedded.PreparedDbProvider
import no.nav.su.se.bakover.common.infrastructure.persistence.Flyway
import no.nav.su.se.bakover.database.Postgres
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.sql.Connection
import javax.sql.DataSource

/** Kjører kun flyway-migrering på første kallet, bruker templates for å opprette nye databaser. */
@TestOnly
fun withMigratedDb(
    test: (dataSource: DataSource) -> Unit,
) {
    test(createNewDatabase())
}

// TODO: se over bruken av denne.
@TestOnly
fun migratedDb(): DataSource {
    return createNewDatabase()
}

private fun ensureRoleExists(conn: Connection, role: String) {
    conn.prepareStatement(
        """
        DO $$
        BEGIN
            CREATE ROLE "$role-${Postgres.Role.Admin}";
        EXCEPTION WHEN DUPLICATE_OBJECT THEN
            RAISE NOTICE 'Role $role already exists';
        END
        $$;
        """.trimIndent(),
    ).use { it.execute() }
}

private fun ensureExtensions(conn: Connection) {
    conn.prepareStatement("""CREATE EXTENSION IF NOT EXISTS "uuid-ossp"""").use { it.execute() }
}

/*
Creating the template database is slow because PostgreSQL must run all your Flyway migrations and build your entire schema from scratch.
Derfor lages denne en gang per test mens createNewDb kjører per test
 */
private fun createTemplate(): PreparedDbProvider {
    return PreparedDbProvider.forPreparer(CustomFlywayPreparer())
}

/*
Creating a new test database is fast because PostgreSQL simply clones the already-prepared template at the filesystem level without re-running any migrations.
 */
private fun createNewDb(provider: PreparedDbProvider): DataSource {
    val info = provider.createNewDatabase()
    return provider.createDataSourceFromConnectionInfo(info)
}

private fun createNewDatabase(): DataSource {
    val provider = PreparedDbProvider.forPreparer(CustomFlywayPreparer())
    val info = provider.createNewDatabase()
    return provider.createDataSourceFromConnectionInfo(info)
}

private class CustomFlywayPreparer(
    val role: String = "postgres",
) : DatabasePreparer {
    override fun prepare(ds: DataSource) {
        ds.connection.use { connection ->
            ensureRoleExists(connection, role)
            ensureExtensions(connection)
            Flyway(ds, role).migrate()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CustomFlywayPreparer

        if (role != other.role) return false

        return true
    }

    override fun hashCode(): Int {
        var result = role.hashCode()
        result *= 31
        return result
    }
}

/*
    ExtensionContext er per jvm, så når man kjører test av hele repoet fungerer det enda raskere.
    Kjører man tester kun for ett prosjekt med flere jvms mister man denne speedupen.
    Vi får uansett en speed up at createTemplate() kun blir kalt en gang per jvm.
    En annen måte hadde vært å kjøre en single jvm for feks database modulen med mange tråder men ser ikke ut som den benytter
    alle kjerner som er tilgjengelig.
    tldr; gir speedup fordi template bare blir laget en gang per jvm i testkjøring
 */
class DbExtension : ParameterResolver {
    companion object {
        private const val PROVIDER_KEY = "GLOBAL_PROVIDER"
    }
    private fun getProvider(context: ExtensionContext): PreparedDbProvider {
        val rootStore = context.root.getStore(ExtensionContext.Namespace.GLOBAL)
        return rootStore.getOrComputeIfAbsent(PROVIDER_KEY) {
            createTemplate()
        } as PreparedDbProvider
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == DataSource::class.java
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): DataSource {
        if (parameterContext.parameter?.type == DataSource::class.java) {
            return createNewDb(getProvider(extensionContext))
        } else {
            throw IllegalArgumentException("Kan ikke resolve parameter av type ${parameterContext.parameter?.type}")
        }
    }
}
