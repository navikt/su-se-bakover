package no.nav.su.se.bakover

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.util.KtorExperimentalAPI
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.db.DataSourceBuilder

/** understands the need for a postgres db in memory that can live and die with our component tests*/
@KtorExperimentalAPI
class EmbeddedDatabase {
    companion object {
        val instance: EmbeddedPostgres = EmbeddedPostgres.builder()
            .setLocaleConfig("locale", "en_US.UTF-8") //Feiler med Process [/var/folders/l2/q666s90d237c37rwkw9x71bw0000gn/T/embedded-pg/PG-73dc0043fe7bdb624d5e8726bc457b7e/bin/initdb ...  hvis denne ikke er med.
            .start()!!
        init {
            instance.getDatabase(DB_NAME, DB_NAME)
                .connection
                .prepareStatement("""create role "$DB_NAME-${DataSourceBuilder.Role.Admin}" """)
                .execute()//Må legge til rollen i databasen for at Flyway skal få kjørt migrering.
        }
        fun refresh() {
            using(sessionOf(HikariDataSource(configure(instance.getJdbcUrl(DB_NAME, DB_NAME))))) {
                it.run(queryOf("truncate sak restart identity cascade").asExecute)
            }
        }
        private fun configure(jdbcUrl: String) =
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                maximumPoolSize = 3
                minimumIdle = 1
                idleTimeout = 10001
                connectionTimeout = 1000
                maxLifetime = 30001
            }
    }
}