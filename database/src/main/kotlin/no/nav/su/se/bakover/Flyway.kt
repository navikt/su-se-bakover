package no.nav.su.se.bakover

import no.nav.su.se.bakover.Postgres.Role.Admin
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class Flyway(
        private val dataSource: DataSource,
        private val databaseName: String
) {
    fun migrate() {
        runMigration(dataSource)
    }

    private fun runMigration(dataSource: DataSource) =
            Flyway.configure()
                    .dataSource(dataSource)
                    .initSql("SET ROLE \"$databaseName-$Admin\"")//Pga roterende credentials i preprod/prod må tabeller opprettes/endres av samme rolle hver gang. Se https://github.com/navikt/utvikling/blob/master/PostgreSQL.md#hvordan-kj%C3%B8re-flyway-migreringerendre-p%C3%A5-databaseskjemaet
                    .load()
                    .migrate()
}