package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.behandling.BehandlingServiceImpl
import no.nav.su.se.bakover.service.brev.BrevServiceImpl
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
        val sakService = SakServiceImpl(
            sakRepo = databaseRepos.sak
        )
        val utbetalingService = UtbetalingServiceImpl(
            utbetalingRepo = databaseRepos.utbetaling,
            sakRepo = databaseRepos.sak
        )
        val søknadService = SøknadServiceImpl(
            søknadRepo = databaseRepos.søknad,
            sakService = sakService,
            brevService = BrevServiceImpl(
                pdfGenerator = clients.pdfGenerator,
                personOppslag = clients.personOppslag,
                dokArkiv = clients.dokArkiv,
                dokDistFordeling = clients.dokDistFordeling,
                sakService = sakService
            )
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
                simuleringClient = clients.simuleringClient,
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
                simuleringClient = clients.simuleringClient,
                utbetalingPublisher = clients.utbetalingPublisher,
                utbetalingService = utbetalingService,
                sakService = sakService
            ),
            startUtbetalinger = StartUtbetalingerService(
                simuleringClient = clients.simuleringClient,
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
