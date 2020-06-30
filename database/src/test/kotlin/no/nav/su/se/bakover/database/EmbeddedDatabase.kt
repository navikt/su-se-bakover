package no.nav.su.se.bakover.database

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import javax.sql.DataSource
import no.nav.su.se.bakover.database.EmbeddedDatabase.DB_NAME
import org.flywaydb.core.Flyway

object EmbeddedDatabase {
    internal val DB_NAME = "postgres"
    private val instance = EmbeddedPostgres.builder()
            // Don't explicit set locale here, because it will auto-detect differently on mac osx, windows and linux.
            // If you locale is set to C, you have to fix your locale: https://stackoverflow.com/questions/7165108/in-os-x-lion-lang-is-not-set-to-utf-8-how-to-fix-it
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
