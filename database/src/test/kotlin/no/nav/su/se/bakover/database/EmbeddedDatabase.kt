package no.nav.su.se.bakover.database

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.su.se.bakover.database.EmbeddedDatabase.DB_NAME
import org.flywaydb.core.Flyway
import javax.sql.DataSource

object EmbeddedDatabase {
    internal const val DB_NAME = "postgres"
    private val instance = EmbeddedPostgres.builder()
        // Don't explicit set locale here, because it will auto-detect differently on mac osx, windows and linux.
        // See PR: https://github.com/opentable/otj-pg-embedded/pull/89/files
        // If you locale is set to C, you have to fix your locale: https://stackoverflow.com/questions/7165108/in-os-x-lion-lang-is-not-set-to-utf-8-how-to-fix-it
        // E.g. add `export LANG=en_US.UTF-8` in .zshrc/.bash_profile/.profile/config.fish
        .start()!!.also {
        creatAdminRole(it)
    }

    private fun creatAdminRole(embeddedPostgres: EmbeddedPostgres) {
        embeddedPostgres.getDatabase(DB_NAME, DB_NAME)
            .connection
            .prepareStatement("""create role "$DB_NAME-${Postgres.Role.Admin}" """)
            .execute() // Må legge til rollen i databasen for at Flyway skal få kjørt migrering.
        embeddedPostgres.getDatabase(DB_NAME, DB_NAME)
            .connection
            .prepareStatement("""create EXTENSION IF NOT EXISTS "uuid-ossp"""")
            .execute()
    }

    fun instance(): DataSource = instance.getDatabase(DB_NAME, DB_NAME)
}

fun withMigratedDb(test: () -> Unit) {
    EmbeddedDatabase.instance().also {
        clean(it)
        it.migrateToLatest()
        test()
    }
}

/**
 * Use-case for denne er i utgangspunktet dersom man trenger å skrive en test på en migrering
 */
@Suppress("unused")
fun withDbMigratedToVersion(v: Int, test: () -> Unit) {
    EmbeddedDatabase.instance().also {
        clean(it)
        Flyway(it, DB_NAME).migrateTo(v)
        test()
    }
}

fun DataSource.migrateToLatest() =
    Flyway(this, DB_NAME).migrate()

internal fun clean(dataSource: DataSource) = Flyway.configure()
    .dataSource(dataSource)
    .load()
    .clean()
