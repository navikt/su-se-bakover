package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.behandling.BehandlingServiceImpl
import no.nav.su.se.bakover.service.oppdrag.OppdragService
import no.nav.su.se.bakover.service.oppdrag.OppdragServiceImpl
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.SøknadServiceImpl
import no.nav.su.se.bakover.service.utbetaling.StansUtbetalingService
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingerService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingServiceImpl

class ServiceBuilder(
    private val databaseRepos: DatabaseRepos,
    private val clients: Clients
) {
    fun build(): Services {
        val utbetalingService = UtbetalingServiceImpl(
            repo = databaseRepos.utbetalingRepo,
        )
        val søknadService = SøknadServiceImpl(
            søknadRepo = databaseRepos.søknadRepo
        )
        val sakService = SakServiceImpl(
            sakRepo = databaseRepos.sakRepo
        )
        return Services(
            avstemmingService = AvstemmingServiceImpl(
                repo = databaseRepos.avstemmingRepo,
                publisher = clients.avstemmingPublisher
            ),
            utbetalingService = utbetalingService,
            oppdragService = OppdragServiceImpl(
                repo = databaseRepos.oppdragRepo
            ),
            behandlingService = BehandlingServiceImpl(
                behandlingRepo = databaseRepos.behandlingRepo,
                hendelsesloggRepo = databaseRepos.hendelsesloggRepo,
                beregningRepo = databaseRepos.beregningRepo,
                oppdragRepo = databaseRepos.oppdragRepo,
                simuleringClient = clients.simuleringClient,
                utbetalingService = utbetalingService,
                oppgaveClient = clients.oppgaveClient,
                utbetalingPublisher = clients.utbetalingPublisher,
                søknadService = søknadService,
                sakService = sakService
            ),
            sakService = sakService,
            søknadService = søknadService,
            stansUtbetalingService = StansUtbetalingService(
                simuleringClient = clients.simuleringClient,
                utbetalingPublisher = clients.utbetalingPublisher,
                utbetalingService = utbetalingService
            ),
            startUtbetalingerService = StartUtbetalingerService(
                simuleringClient = clients.simuleringClient,
                utbetalingPublisher = clients.utbetalingPublisher,
                utbetalingService = utbetalingService,
                sakService = sakService
            )
        )
    }
}

data class Services(
    val avstemmingService: AvstemmingService,
    val utbetalingService: UtbetalingService,
    val oppdragService: OppdragService,
    val behandlingService: BehandlingService,
    val sakService: SakService,
    val søknadService: SøknadService,
    val stansUtbetalingService: StansUtbetalingService,
    val startUtbetalingerService: StartUtbetalingerService
)
