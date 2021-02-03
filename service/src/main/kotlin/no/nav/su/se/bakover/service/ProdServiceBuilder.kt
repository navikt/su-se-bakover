package no.nav.su.se.bakover.service

import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.behandling.BehandlingServiceImpl
import no.nav.su.se.bakover.service.behandling.DistribuerIverksettingsbrevService
import no.nav.su.se.bakover.service.behandling.IverksettBehandlingService
import no.nav.su.se.bakover.service.behandling.JournalførIverksettingService
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.brev.BrevServiceImpl
import no.nav.su.se.bakover.service.oppgave.OppgaveServiceImpl
import no.nav.su.se.bakover.service.person.PersonServiceImpl
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.statistikk.StatistikkServiceImpl
import no.nav.su.se.bakover.service.søknad.SøknadServiceImpl
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.FerdigstillSøknadsbehandingIverksettingServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.service.toggles.ToggleServiceImpl
import no.nav.su.se.bakover.service.utbetaling.UtbetalingServiceImpl
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import java.time.Clock

object ProdServiceBuilder : ServiceBuilder {
    override fun build(
        databaseRepos: DatabaseRepos,
        clients: Clients,
        behandlingMetrics: BehandlingMetrics,
        søknadMetrics: SøknadMetrics,
        clock: Clock,
        unleash: Unleash
    ): Services {
        val personService = PersonServiceImpl(clients.personOppslag)
        val statistikkService = StatistikkServiceImpl(clients.kafkaPublisher, personService, clock)
        val sakService = SakServiceImpl(
            sakRepo = databaseRepos.sak
        ).apply { observers.add(statistikkService) }
        val utbetalingService = UtbetalingServiceImpl(
            utbetalingRepo = databaseRepos.utbetaling,
            sakService = sakService,
            simuleringClient = clients.simuleringClient,
            utbetalingPublisher = clients.utbetalingPublisher,
            clock = clock,
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
            sakFactory = SakFactory(clock = clock),
            pdfGenerator = clients.pdfGenerator,
            dokArkiv = clients.dokArkiv,
            personService = personService,
            oppgaveService = oppgaveService,
            søknadMetrics = søknadMetrics,
            clock = clock,
        )
        val opprettVedtakssnapshotService = OpprettVedtakssnapshotService(databaseRepos.vedtakssnapshot)
        val journalførIverksettingService = JournalførIverksettingService(databaseRepos.behandling, brevService)
        val distribuerIverksettingsbrevService =
            DistribuerIverksettingsbrevService(brevService, databaseRepos.behandling)
        val ferdigstillIverksettingService = FerdigstillSøknadsbehandingIverksettingServiceImpl(
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            oppgaveService = oppgaveService,
            behandlingMetrics = behandlingMetrics,
            microsoftGraphApiClient = clients.microsoftGraphApiClient,
            personService = personService,
            brevService = brevService
        )
        val toggleService = ToggleServiceImpl(unleash)

        val iverksettSaksbehandlingService = IverksettSøknadsbehandlingService(
            utbetalingService = utbetalingService,
            oppgaveService = oppgaveService,
            personService = personService,
            behandlingMetrics = behandlingMetrics,
            microsoftGraphApiClient = clients.microsoftGraphApiClient,
            clock = clock,
            brevService = brevService,
        )

        return Services(
            avstemming = AvstemmingServiceImpl(
                repo = databaseRepos.avstemming,
                publisher = clients.avstemmingPublisher,
                clock = clock,
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
                clock = clock,
                iverksettBehandlingService = IverksettBehandlingService(
                    behandlingRepo = databaseRepos.behandling,
                    utbetalingService = utbetalingService,
                    oppgaveService = oppgaveService,
                    personService = personService,
                    behandlingMetrics = behandlingMetrics,
                    microsoftGraphApiClient = clients.microsoftGraphApiClient,
                    opprettVedtakssnapshotService = opprettVedtakssnapshotService,
                    clock = clock,
                    journalførIverksettingService = journalførIverksettingService,
                    distribuerIverksettingsbrevService = distribuerIverksettingsbrevService,
                ).apply { addObserver(statistikkService) },
            ).apply { addObserver(statistikkService) },
            sak = sakService,
            søknad = søknadService,
            brev = brevService,
            lukkSøknad = LukkSøknadServiceImpl(
                søknadRepo = databaseRepos.søknad,
                sakService = sakService,
                brevService = brevService,
                oppgaveService = oppgaveService,
                personService = personService,
                clock = clock,
            ),
            oppgave = oppgaveService,
            person = personService,
            statistikk = statistikkService,
            toggles = toggleService,
            søknadsbehandling = SøknadsbehandlingServiceImpl(
                søknadService = søknadService,
                søknadRepo = databaseRepos.søknad,
                søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
                utbetalingService = utbetalingService,
                personService = personService,
                oppgaveService = oppgaveService,
                iverksettSøknadsbehandlingService = iverksettSaksbehandlingService,
                behandlingMetrics = behandlingMetrics,
                beregningService = BeregningService(),
                microsoftGraphApiClient = clients.microsoftGraphApiClient,
                brevService = brevService
            ).apply {
                addObserver(statistikkService)
            },
            ferdigstillSøknadsbehandingIverksettingService = ferdigstillIverksettingService,
        )
    }
}
