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
            utbetalingRepo = databaseRepos.utbetaling,
            sakRepo = databaseRepos.sak,
            simuleringClient = clients.simuleringClient
        )
        val søknadService = SøknadServiceImpl(
            søknadRepo = databaseRepos.søknad
        )
        val sakService = SakServiceImpl(
            sakRepo = databaseRepos.sak
        )
        return Services(
            avstemming = AvstemmingServiceImpl(
                repo = databaseRepos.avstemming,
                publisher = clients.avstemmingPublisher
            ),
            utbetaling = utbetalingService,
            oppdrag = OppdragServiceImpl(
                repo = databaseRepos.oppdrag
            ),
            behandling = BehandlingServiceImpl(
                behandlingRepo = databaseRepos.behandling,
                hendelsesloggRepo = databaseRepos.hendelseslogg,
                beregningRepo = databaseRepos.beregning,
                oppdragRepo = databaseRepos.oppdrag,
                utbetalingService = utbetalingService,
                oppgaveClient = clients.oppgaveClient,
                utbetalingPublisher = clients.utbetalingPublisher,
                søknadService = søknadService,
                sakService = sakService,
                personOppslag = clients.personOppslag
            ),
            sak = sakService,
            søknad = søknadService,
            stansUtbetaling = StansUtbetalingService(
                utbetalingPublisher = clients.utbetalingPublisher,
                utbetalingService = utbetalingService,
                sakService = sakService
            ),
            startUtbetalinger = StartUtbetalingerService(
                utbetalingPublisher = clients.utbetalingPublisher,
                utbetalingService = utbetalingService,
                sakService = sakService
            )
        )
    }
}

data class Services(
    val avstemming: AvstemmingService,
    val utbetaling: UtbetalingService,
    val oppdrag: OppdragService,
    val behandling: BehandlingService,
    val sak: SakService,
    val søknad: SøknadService,
    val stansUtbetaling: StansUtbetalingService,
    val startUtbetalinger: StartUtbetalingerService
)
