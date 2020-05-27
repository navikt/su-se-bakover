package no.nav.su.se.bakover.db

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.su.se.bakover.Postgres
import org.flywaydb.core.Flyway
import javax.sql.DataSource

object EmbeddedDatabase {
    private val DB_NAME = "postgres"
    private val JDBC_FORMAT = "jdbc:postgresql://localhost:%s/%s?user=%s"
    private val instance = EmbeddedPostgres.builder()
            .setLocaleConfig("locale", "en_US.UTF-8") //Feiler med Process [/var/folders/l2/q666s90d237c37rwkw9x71bw0000gn/T/embedded-pg/PG-73dc0043fe7bdb624d5e8726bc457b7e/bin/initdb ...  hvis denne ikke er med.
            .start()!!.also {
                creatAdminRole(it)
            }
    private fun creatAdminRole(embeddedPostgres: EmbeddedPostgres) {
        embeddedPostgres.getDatabase(DB_NAME, DB_NAME)
                .connection
                .prepareStatement("""create role "$DB_NAME-${Postgres.Role.Admin}" """)
                .execute()//Må legge til rollen i databasen for at Flyway skal få kjørt migrering.
    }

    val database = instance.getDatabase(DB_NAME, DB_NAME)

    fun getEmbeddedJdbcUrl() = String.format(JDBC_FORMAT, instance.port, DB_NAME, DB_NAME)
}

fun withMigratedDb(test: () -> Unit) =
        EmbeddedDatabase.database.also { clean(it) }.also { migrate(it) }.run { test() }

fun migrate(dataSource: DataSource, initSql: String = ""): Int =
        Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .initSql(initSql)
                .load()
                .migrate()

fun clean(dataSource: DataSource) = Flyway.configure()
        .dataSource(dataSource)
        .load()
        .clean()