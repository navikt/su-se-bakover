package no.nav.su.se.bakover.database

import io.zonky.test.db.postgres.embedded.DatabasePreparer
import io.zonky.test.db.postgres.embedded.PreparedDbProvider
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("EmbeddedDatabase.kt")

// private var preparers: MutableList<CustomFlywayPreparer> = CopyOnWriteArrayList(listOf(CustomFlywayPreparer()))
private var preparer: CustomFlywayPreparer = CustomFlywayPreparer()

/** Kjører kun flyway-migrering på første kallet, bruker templates for å opprette nye databaser. */
fun withMigratedDb(test: (dataSource: DataSource) -> Unit) {
    test(createNewDatabase())
}

/** Brukes fra web-laget */
@Suppress("unused")
fun migratedDb(): DataSource {
    return createNewDatabase()
}

private fun createNewDatabase(): DataSource {
    val provider = PreparedDbProvider.forPreparer(preparer)
    val info = provider.createNewDatabase()
    return provider.createDataSourceFromConnectionInfo(info)
}

// private fun createNewDatabase(): DataSource {
//     preparers.forEach { preparer ->
//         tryCreateNewDatabase(PreparedDbProvider.forPreparer(preparer)).map { dataSource ->
//             return dataSource
//         }
//     }
//     return CustomFlywayPreparer().let { preparer ->
//         preparers.add(preparer)
//         tryCreateNewDatabase(PreparedDbProvider.forPreparer(preparer)).getOrHandle {
//             throw IllegalStateException("Failed to create new PreparedDbProvider when previous one failed", it)
//         }
//     }
// }
//
// private fun tryCreateNewDatabase(provider: PreparedDbProvider): Either<Throwable, DataSource> {
//     return Either.catch {
//         val info = provider.createNewDatabase()
//         provider.createDataSourceFromConnectionInfo(info)
//     }
// }

private class CustomFlywayPreparer(val role: String = "postgres", val toVersion: Int? = null) : DatabasePreparer {
    override fun prepare(ds: DataSource) {
        log.info("Preparing and migrating database for tests ...")
        ds.connection.use { connection ->
            connection
                //language=SQL
                .prepareStatement("""create role "$role-${Postgres.Role.Admin}" """).use {
                    it.execute() // Må legge til rollen i databasen for at Flyway skal få kjørt migrering.
                }
            connection
                //language=SQL
                .prepareStatement("""create EXTENSION IF NOT EXISTS "uuid-ossp"""").use {
                    it.execute()
                }
            if (toVersion != null) {
                Flyway(ds, role).migrateTo(toVersion)
            } else {
                Flyway(ds, role).migrate()
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CustomFlywayPreparer

        if (role != other.role) return false
        if (toVersion != other.toVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = role.hashCode()
        result = 31 * result + (toVersion ?: 0)
        return result
    }
}
