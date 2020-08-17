package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.Config
import org.flywaydb.core.Flyway
import javax.sql.DataSource

internal class Flyway(
    private val dataSource: DataSource,
    private val databaseName: String
) {
    fun migrate() = runMigration(dataSource)

    private fun runMigration(dataSource: DataSource) =
        Flyway.configure()
            .dataSource(dataSource).let {
                // Lokalt ønsker vi ikke noe herjing med rolle; Docker-oppsettet sørger for at vi har riktige tilganger her.
                if (Config.isLocalOrRunningTests)
                    it
                else
                    it.initSql("SET ROLE \"$databaseName-${Postgres.Role.Admin}\"") // Pga roterende credentials i preprod/prod må tabeller opprettes/endres av samme rolle hver gang. Se https://github.com/navikt/utvikling/blob/master/PostgreSQL.md#hvordan-kj%C3%B8re-flyway-migreringerendre-p%C3%A5-databaseskjemaet
            }
            .load()
            .migrate()
}
