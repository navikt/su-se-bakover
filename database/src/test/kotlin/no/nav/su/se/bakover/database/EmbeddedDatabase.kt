package no.nav.su.se.bakover.database

import arrow.core.Either
import arrow.core.getOrHandle
import io.zonky.test.db.postgres.embedded.DatabasePreparer
import io.zonky.test.db.postgres.embedded.PreparedDbProvider
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("EmbeddedDatabase.kt")

private var preparers: MutableList<CustomFlywayPreparer> = CopyOnWriteArrayList(listOf(CustomFlywayPreparer()))

/**
 * Kjører kun flyway-migrering på første kallet, bruker templates for å opprette nye databaser.
 */
fun withMigratedDb(test: (dataSource: DataSource) -> Unit) {
    test(createNewDatabase())
}

/**
 * Brukes fra web-laget
 */
@Suppress("unused")
fun migratedDb(): DataSource {
    return createNewDatabase()
}

/**
 * Use-case for denne er i utgangspunktet dersom man trenger å skrive en test på en migrering.
 * Denne vil gjøre en ny migrering for hver gang den er kalt.
 * Bør se på å cache migreringene dersom de samme versjonene brukes fler ganger.
 */
// @Suppress("unused")
// fun withDbMigratedToVersion(v: Int, test: (dataSource: DataSource) -> Unit) {
//     test(createNewDatabase(CustomFlywayPreparer(toVersion = v)))
// }

private fun createNewDatabase(): DataSource {
    preparers.forEach { preparer ->
        tryCreateNewDatabase(PreparedDbProvider.forPreparer(preparer)).map { dataSource ->
            return dataSource
        }
    }
    return CustomFlywayPreparer().let { preparer ->
        preparers.add(preparer)
        tryCreateNewDatabase(PreparedDbProvider.forPreparer(preparer)).getOrHandle {
            throw IllegalStateException("Failed to create new PreparedDbProvider when previous one failed", it)
        }
    }
}

private fun tryCreateNewDatabase(provider: PreparedDbProvider): Either<Throwable, DataSource> {
    return Either.catch {
        val info = provider.createNewDatabase()
        provider.createDataSourceFromConnectionInfo(info)
    }
}

private class CustomFlywayPreparer(val role: String = "postgres", val toVersion: Int? = null) : DatabasePreparer {
    override fun prepare(ds: DataSource) {
        log.info("Preparing and migrating database for tests ...")
        ds.connection
            .prepareStatement("""create role "$role-${Postgres.Role.Admin}" """)
            .execute() // Må legge til rollen i databasen for at Flyway skal få kjørt migrering.
        ds.connection
            .prepareStatement("""create EXTENSION IF NOT EXISTS "uuid-ossp"""")
            .execute()
        if (toVersion != null) {
            Flyway(ds, role).migrateTo(toVersion)
        } else {
            Flyway(ds, role).migrate()
        }
    }
}
