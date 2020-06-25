package no.nav.su.se.bakover.database

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import javax.sql.DataSource
import no.nav.su.se.bakover.database.EmbeddedDatabase.DB_NAME
import org.flywaydb.core.Flyway

object EmbeddedDatabase {
    internal val DB_NAME = "postgres"
    private val instance = EmbeddedPostgres.builder()
            .setLocaleConfig("locale", "en_US.UTF-8") // Feiler med Process [/var/folders/l2/q666s90d237c37rwkw9x71bw0000gn/T/embedded-pg/PG-73dc0043fe7bdb624d5e8726bc457b7e/bin/initdb ...  hvis denne ikke er med.
            .start()!!.also {
                creatAdminRole(it)
            }

    private fun creatAdminRole(embeddedPostgres: EmbeddedPostgres) {
        embeddedPostgres.getDatabase(DB_NAME, DB_NAME)
                .connection
                .prepareStatement("""create role "$DB_NAME-${Postgres.Role.Admin}" """)
                .execute() // Må legge til rollen i databasen for at Flyway skal få kjørt migrering.
    }

    fun instance(): DataSource = instance.getDatabase(DB_NAME, DB_NAME)
}

fun withMigratedDb(test: () -> Unit) {
    EmbeddedDatabase.instance().also {
        clean(it)
        Flyway(it, DB_NAME).migrate()
        test()
    }
}

internal fun clean(dataSource: DataSource) = Flyway.configure()
        .dataSource(dataSource)
        .load()
        .clean()
