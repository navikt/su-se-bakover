package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.database.avstemming.AvstemmingPostgresRepo
import no.nav.su.se.bakover.database.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.database.behandling.BehandlingPostgresRepo
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggPostgresRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.person.PersonPostgresRepo
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotPostgresRepo
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import javax.sql.DataSource

object DatabaseBuilder {
    fun build(behandlingFactory: BehandlingFactory): DatabaseRepos {
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
        return buildInternal(userDatastore, behandlingFactory)
    }

    fun build(embeddedDatasource: DataSource, behandlingFactory: BehandlingFactory): DatabaseRepos {
        Flyway(embeddedDatasource, "postgres").migrate()
        return buildInternal(embeddedDatasource, behandlingFactory)
    }

    private fun buildInternal(dataSource: DataSource, behandlingFactory: BehandlingFactory): DatabaseRepos {
        val behandlingRepo = BehandlingPostgresRepo(dataSource, behandlingFactory)
        return DatabaseRepos(
            avstemming = AvstemmingPostgresRepo(dataSource),
            utbetaling = UtbetalingPostgresRepo(dataSource),
            søknad = SøknadPostgresRepo(dataSource),
            behandling = behandlingRepo,
            hendelseslogg = HendelsesloggPostgresRepo(dataSource),
            sak = SakPostgresRepo(dataSource, behandlingRepo),
            person = PersonPostgresRepo(dataSource),
            vedtakssnapshot = VedtakssnapshotPostgresRepo(dataSource)
        )
    }
}

data class DatabaseRepos(
    val avstemming: AvstemmingRepo,
    val utbetaling: UtbetalingRepo,
    val søknad: SøknadRepo,
    val behandling: BehandlingRepo,
    val hendelseslogg: HendelsesloggRepo,
    val sak: SakRepo,
    val person: PersonRepo,
    val vedtakssnapshot: VedtakssnapshotRepo
)
