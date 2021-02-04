package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.RotatingCredentials
import no.nav.su.se.bakover.common.ApplicationConfig.DatabaseConfig.StaticCredentials
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
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotPostgresRepo
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import javax.sql.DataSource

object DatabaseBuilder {
    fun build(behandlingFactory: BehandlingFactory, databaseConfig: ApplicationConfig.DatabaseConfig): DatabaseRepos {
        val abstractDatasource = Postgres(databaseConfig = databaseConfig).build()

        val dataSource = abstractDatasource.getDatasource(Postgres.Role.Admin)
        when (databaseConfig) {
            is StaticCredentials -> {
                // Lokalt ønsker vi ikke noe herjing med rolle; Docker-oppsettet sørger for at vi har riktige tilganger her.
                Flyway(dataSource)
            }
            is RotatingCredentials -> Flyway(
                dataSource = dataSource,
                // Pga roterende credentials i preprod/prod må tabeller opprettes/endres av samme rolle hver gang. Se https://github.com/navikt/utvikling/blob/master/PostgreSQL.md#hvordan-kj%C3%B8re-flyway-migreringerendre-p%C3%A5-databaseskjemaet
                role = "${databaseConfig.databaseName}-${Postgres.Role.Admin}"
            )
        }.migrate()

        val userDatastore = abstractDatasource.getDatasource(Postgres.Role.User)
        return buildInternal(userDatastore, behandlingFactory)
    }

    fun build(embeddedDatasource: DataSource, behandlingFactory: BehandlingFactory): DatabaseRepos {
        // I testene ønsker vi ikke noe herjing med rolle; embedded-oppsettet sørger for at vi har riktige tilganger her.
        Flyway(embeddedDatasource).migrate()
        return buildInternal(embeddedDatasource, behandlingFactory)
    }

    private fun buildInternal(dataSource: DataSource, behandlingFactory: BehandlingFactory): DatabaseRepos {
        val behandlingRepo = BehandlingPostgresRepo(dataSource, behandlingFactory)
        val saksbehandlingRepo = SøknadsbehandlingPostgresRepo(dataSource)
        return DatabaseRepos(
            avstemming = AvstemmingPostgresRepo(dataSource),
            utbetaling = UtbetalingPostgresRepo(dataSource),
            søknad = SøknadPostgresRepo(dataSource),
            behandling = behandlingRepo,
            hendelseslogg = HendelsesloggPostgresRepo(dataSource),
            sak = SakPostgresRepo(dataSource, saksbehandlingRepo),
            person = PersonPostgresRepo(dataSource),
            vedtakssnapshot = VedtakssnapshotPostgresRepo(dataSource),
            søknadsbehandling = saksbehandlingRepo,
            revurderingRepo = RevurderingPostgresRepo(dataSource, saksbehandlingRepo),
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
    val vedtakssnapshot: VedtakssnapshotRepo,
    val søknadsbehandling: SøknadsbehandlingRepo,
    val revurderingRepo: RevurderingRepo,
)
