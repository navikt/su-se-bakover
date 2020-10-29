package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.behandling.BehandlingServiceImpl
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.BrevServiceImpl
import no.nav.su.se.bakover.service.oppdrag.OppdragService
import no.nav.su.se.bakover.service.oppdrag.OppdragServiceImpl
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.oppgave.OppgaveServiceImpl
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.SøknadServiceImpl
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadServiceImpl
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingServiceImpl

class ServiceBuilder(
    private val databaseRepos: DatabaseRepos,
    private val clients: Clients
) {
    fun build(): Services {
        val accessCheckProxy = AccessCheckProxy(databaseRepos.person, clients)

        val sakService = SakServiceImpl(
            sakRepo = databaseRepos.sak
        )
        val utbetalingService = UtbetalingServiceImpl(
            utbetalingRepo = databaseRepos.utbetaling,
            sakService = sakService,
            simuleringClient = clients.simuleringClient,
            utbetalingPublisher = clients.utbetalingPublisher
        )
        val brevService = BrevServiceImpl(
            pdfGenerator = clients.pdfGenerator,
            personOppslag = clients.personOppslag,
            dokArkiv = clients.dokArkiv,
            dokDistFordeling = clients.dokDistFordeling
        )
        val oppgaveService = OppgaveServiceImpl(
            oppgaveClient = clients.oppgaveClient
        )
        val søknadService = SøknadServiceImpl(
            søknadRepo = databaseRepos.søknad,
            sakService = sakService,
            sakFactory = SakFactory(),
            pdfGenerator = clients.pdfGenerator,
            dokArkiv = clients.dokArkiv,
            personOppslag = clients.personOppslag,
            oppgaveClient = clients.oppgaveClient
        )
        return accessCheckProxy.proxy(
            Services(
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
                    utbetalingService = utbetalingService,
                    oppgaveClient = clients.oppgaveClient,
                    søknadService = søknadService,
                    sakService = sakService,
                    personOppslag = clients.personOppslag,
                    brevService = brevService
                ),
                sak = sakService,
                søknad = søknadService,
                brev = brevService,
                lukkSøknad = LukkSøknadServiceImpl(
                    søknadRepo = databaseRepos.søknad,
                    sakService = sakService,
                    brevService = brevService,
                    oppgaveService = oppgaveService
                ),
                oppgave = OppgaveServiceImpl(
                    oppgaveClient = clients.oppgaveClient
                )
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
    val brev: BrevService,
    val lukkSøknad: LukkSøknadService,
    val oppgave: OppgaveService
)
