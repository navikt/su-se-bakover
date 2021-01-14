package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.behandling.BehandlingServiceImpl
import no.nav.su.se.bakover.service.behandling.DistribuerIverksettingsbrevService
import no.nav.su.se.bakover.service.behandling.FerdigstillIverksettingService
import no.nav.su.se.bakover.service.behandling.IverksettBehandlingService
import no.nav.su.se.bakover.service.behandling.JournalførIverksettingService
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.BrevServiceImpl
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.oppgave.OppgaveServiceImpl
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.person.PersonServiceImpl
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.statistikk.StatistikkService
import no.nav.su.se.bakover.service.statistikk.StatistikkServiceImpl
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.SøknadServiceImpl
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadServiceImpl
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingServiceImpl
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import java.time.Clock

class ServiceBuilder(
    private val databaseRepos: DatabaseRepos,
    private val clients: Clients,
    private val behandlingMetrics: BehandlingMetrics,
    private val søknadMetrics: SøknadMetrics
) {
    fun build(): Services {
        val personService = PersonServiceImpl(clients.personOppslag)
        val statistikkService = StatistikkServiceImpl(clients.kafkaPublisher, personService)
        val sakService = SakServiceImpl(
            sakRepo = databaseRepos.sak
        ).apply { observers.add(statistikkService) }
        val utbetalingService = UtbetalingServiceImpl(
            utbetalingRepo = databaseRepos.utbetaling,
            sakService = sakService,
            simuleringClient = clients.simuleringClient,
            utbetalingPublisher = clients.utbetalingPublisher
        )
        val brevService = BrevServiceImpl(
            pdfGenerator = clients.pdfGenerator,
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
            personService = personService,
            oppgaveService = oppgaveService,
            søknadMetrics = søknadMetrics
        )
        val opprettVedtakssnapshotService = OpprettVedtakssnapshotService(databaseRepos.vedtakssnapshot)
        val journalførIverksettingService = JournalførIverksettingService(databaseRepos.behandling, brevService)
        val distribuerIverksettingsbrevService =
            DistribuerIverksettingsbrevService(brevService, databaseRepos.behandling)
        val ferdigstillIverksettingService = FerdigstillIverksettingService(
            behandlingRepo = databaseRepos.behandling,
            oppgaveService = oppgaveService,
            personService = personService,
            behandlingMetrics = behandlingMetrics,
            microsoftGraphApiClient = clients.microsoftGraphApiClient,
            journalførIverksettingService = journalførIverksettingService,
            distribuerIverksettingsbrevService = distribuerIverksettingsbrevService,
        )

        return Services(
            avstemming = AvstemmingServiceImpl(
                repo = databaseRepos.avstemming,
                publisher = clients.avstemmingPublisher
            ),
            utbetaling = utbetalingService,
            behandling = BehandlingServiceImpl(
                behandlingRepo = databaseRepos.behandling,
                hendelsesloggRepo = databaseRepos.hendelseslogg,
                utbetalingService = utbetalingService,
                oppgaveService = oppgaveService,
                søknadService = søknadService,
                søknadRepo = databaseRepos.søknad,
                personService = personService,
                brevService = brevService,
                behandlingMetrics = behandlingMetrics,
                microsoftGraphApiClient = clients.microsoftGraphApiClient,
                clock = Clock.systemUTC(),
                iverksettBehandlingService = IverksettBehandlingService(
                    behandlingRepo = databaseRepos.behandling,
                    utbetalingService = utbetalingService,
                    oppgaveService = oppgaveService,
                    personService = personService,
                    behandlingMetrics = behandlingMetrics,
                    microsoftGraphApiClient = clients.microsoftGraphApiClient,
                    opprettVedtakssnapshotService = opprettVedtakssnapshotService,
                    clock = Clock.systemUTC(),
                    journalførIverksettingService = journalførIverksettingService,
                    distribuerIverksettingsbrevService = distribuerIverksettingsbrevService,
                ).apply { addObserver(statistikkService) },
                ferdigstillIverksettingService = ferdigstillIverksettingService,
            ).apply { addObserver(statistikkService) },
            sak = sakService,
            søknad = søknadService,
            brev = brevService,
            lukkSøknad = LukkSøknadServiceImpl(
                søknadRepo = databaseRepos.søknad,
                sakService = sakService,
                brevService = brevService,
                oppgaveService = oppgaveService,
                personService = personService
            ),
            oppgave = oppgaveService,
            person = personService,
            statistikk = statistikkService,
        )
    }
}

data class Services(
    val avstemming: AvstemmingService,
    val utbetaling: UtbetalingService,
    val behandling: BehandlingService,
    val sak: SakService,
    val søknad: SøknadService,
    val brev: BrevService,
    val lukkSøknad: LukkSøknadService,
    val oppgave: OppgaveService,
    val person: PersonService,
    val statistikk: StatistikkService,
)
