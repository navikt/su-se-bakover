package no.nav.su.se.bakover.test.persistence

import io.zonky.test.db.postgres.embedded.DatabasePreparer
import io.zonky.test.db.postgres.embedded.PreparedDbProvider
import no.nav.su.se.bakover.common.infrastructure.persistence.Flyway
import no.nav.su.se.bakover.database.Postgres
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.slf4j.LoggerFactory
import java.sql.Connection
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("EmbeddedDatabase.kt")

/** Kjører kun flyway-migrering på første kallet, bruker templates for å opprette nye databaser. */
@TestOnly
fun withMigratedDb(
    dbMigrationVersion: Int? = null,
    test: (dataSource: DataSource) -> Unit,
) {
    test(createNewDatabase())
}

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

// TODO: rm
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
    Rationalen bak denne er at når man kjører hundrevis av tester
    så hjelper det veldig å gjenbruke templatecreation som tar ca 5 sekunder.
    Typisk scenario er at man har 10 tråder som kan kjøre tester, så man vil aldri ha nok
    tråder til å kjøre alle enkelttestene i en klassetest slik som skjer når man kjører en og en i
    intellij feks. Så med å kun gjøre templatecreation en gang sparer man 5 sekunder per test case
    i en testklasse gitt at vi ikke har mange nok tråder/kjerner til å betjenee alle testene.(Noe vi ikke har)
    Gitt det så vil tråd 1 som kjører db tester i en testklasse x spare masse tid på alle scenarioene i den testklassen.

    Tldr; mange testklasser er bedre om man kun lager en embedded server per testklasse
    TODO:
    mål: Kun lage en template og kopiere denne, det kan gjøres med en ExtensionContext.Store her.
    Men vil første teste hvor mye raskere denne løsningen er. Denne vil gi stor gevinst siden vi har 200 db tester
    : BeforeAllCallback, ExtensionContext.Store.CloseableResource
        override fun beforeAll(context: ExtensionContext) {
        // Store the object in the root store to ensure it's only created once
        val store = context.root.getStore(ExtensionContext.Namespace.GLOBAL)
        store.getOrComputeIfAbsent("myDataSource", { this }, DataSourceExtension::class.java)
    }

    resolveParameter(...
    val templateprovider: PreparedDbProvider = extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).get("POSTGRES_TEMPLATE")


        override fun close() {
        // Cleanup if needed
        if (::dataSource.isInitialized) {
            dataSource.close()
        }
    }

 */
class DbExtension : ParameterResolver {
    private val provider: PreparedDbProvider = createTemplate()

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == DataSource::class.java
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): DataSource {
        if (parameterContext.parameter?.type == DataSource::class.java) {
            return createNewDb(provider)
        } else {
            throw IllegalArgumentException("Kan ikke resolve parameter av type ${parameterContext.parameter?.type}")
        }
    }
}
