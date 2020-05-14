package no.nav.su.se.bakover

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.db.Postgres

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
                .prepareStatement("""create role "$DB_NAME-${Postgres.Role.Admin}" """)
                .execute()//Må legge til rollen i databasen for at Flyway skal få kjørt migrering.
        }
    }
}