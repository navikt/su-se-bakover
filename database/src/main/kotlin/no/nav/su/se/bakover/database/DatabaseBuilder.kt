package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.database.avstemming.AvstemmingPostgresRepo
import no.nav.su.se.bakover.database.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.database.behandling.BehandlingPostgresRepo
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.BeregningPostgresRepo
import no.nav.su.se.bakover.database.beregning.BeregningRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggPostgresRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.oppdrag.OppdragPostgresRepo
import no.nav.su.se.bakover.database.oppdrag.OppdragRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import javax.sql.DataSource

object DatabaseBuilder {
    fun build(): DatabaseRepos {
        val databaseName = Config.databaseName
        val abstractDatasource = Postgres(
            jdbcUrl = Config.jdbcUrl,
            vaultMountPath = Config.vaultMountPath,
            databaseName = databaseName,
            username = "user",
            password = "pwd"
        ).build()

        Flyway(abstractDatasource.getDatasource(Postgres.Role.Admin), databaseName).migrate()

        val userDatastore = abstractDatasource.getDatasource(Postgres.Role.User)
        return DatabaseRepos(
            avstemming = AvstemmingPostgresRepo(userDatastore),
            utbetaling = UtbetalingPostgresRepo(userDatastore),
            oppdrag = OppdragPostgresRepo(userDatastore),
            søknad = SøknadPostgresRepo(userDatastore),
            behandling = BehandlingPostgresRepo(userDatastore),
            hendelseslogg = HendelsesloggPostgresRepo(userDatastore),
            beregning = BeregningPostgresRepo(userDatastore),
            sak = SakPostgresRepo(userDatastore)
        )
    }

    fun build(embeddedDatasource: DataSource): DatabaseRepos {
        Flyway(embeddedDatasource, "postgres").migrate()
        return DatabaseRepos(
            avstemming = AvstemmingPostgresRepo(embeddedDatasource),
            utbetaling = UtbetalingPostgresRepo(embeddedDatasource),
            oppdrag = OppdragPostgresRepo(embeddedDatasource),
            søknad = SøknadPostgresRepo(embeddedDatasource),
            behandling = BehandlingPostgresRepo(embeddedDatasource),
            hendelseslogg = HendelsesloggPostgresRepo(embeddedDatasource),
            beregning = BeregningPostgresRepo(embeddedDatasource),
            sak = SakPostgresRepo(embeddedDatasource)
        )
    }
}

data class DatabaseRepos(
    val avstemming: AvstemmingRepo,
    val utbetaling: UtbetalingRepo,
    val oppdrag: OppdragRepo,
    val søknad: SøknadRepo,
    val behandling: BehandlingRepo,
    val hendelseslogg: HendelsesloggRepo,
    val beregning: BeregningRepo,
    val sak: SakRepo
)
