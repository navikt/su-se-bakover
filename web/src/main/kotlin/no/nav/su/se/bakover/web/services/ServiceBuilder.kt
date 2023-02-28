package no.nav.su.se.bakover.web.services

import io.getunleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.toggle.infrastructure.UnleashToggleClient
import no.nav.su.se.bakover.database.jobcontext.JobContextPostgresRepo
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.setup.KontrollsamtaleSetup
import no.nav.su.se.bakover.service.SendPåminnelserOmNyStønadsperiodeServiceImpl
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.brev.BrevServiceImpl
import no.nav.su.se.bakover.service.klage.KlageServiceImpl
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseServiceImpl
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallServiceImpl
import no.nav.su.se.bakover.service.oppgave.OppgaveServiceImpl
import no.nav.su.se.bakover.service.person.PersonServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.revurdering.GjenopptaYtelseServiceImpl
import no.nav.su.se.bakover.service.revurdering.RevurderingServiceImpl
import no.nav.su.se.bakover.service.revurdering.StansYtelseServiceImpl
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.skatt.SkatteServiceImpl
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonServiceImpl
import no.nav.su.se.bakover.service.søknad.SøknadServiceImpl
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.IverksettSøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingServiceImpl
import no.nav.su.se.bakover.service.utbetaling.UtbetalingServiceImpl
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakServiceImpl
import no.nav.su.se.bakover.service.vedtak.VedtakServiceImpl
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import java.time.Clock

object ServiceBuilder {
    fun build(
        databaseRepos: DatabaseRepos,
        clients: Clients,
        behandlingMetrics: BehandlingMetrics,
        søknadMetrics: SøknadMetrics,
        clock: Clock,
        unleash: Unleash,
        satsFactory: SatsFactory,
        applicationConfig: ApplicationConfig,
        dbMetrics: DbMetrics,
    ): Services {
        val personService = PersonServiceImpl(clients.personOppslag)
        val toggleService = UnleashToggleClient(unleash)

        val statistikkEventObserver = StatistikkEventObserverBuilder(
            kafkaPublisher = clients.kafkaPublisher,
            personService = personService,
            clock = clock,
            gitCommit = applicationConfig.gitCommit,
        ).statistikkService
        val utbetalingService = UtbetalingServiceImpl(
            utbetalingRepo = databaseRepos.utbetaling,
            simuleringClient = clients.simuleringClient,
            utbetalingPublisher = clients.utbetalingPublisher,
            clock = clock,
        )
        val brevService = BrevServiceImpl(
            pdfGenerator = clients.pdfGenerator,
            dokumentRepo = databaseRepos.dokumentRepo,
            personService = personService,
            sessionFactory = databaseRepos.sessionFactory,
            microsoftGraphApiOppslag = clients.identClient,
            utbetalingService = utbetalingService,
            clock = clock,
            satsFactory = satsFactory,
        )
        val sakService = SakServiceImpl(
            sakRepo = databaseRepos.sak,
            clock = clock,
            dokumentRepo = databaseRepos.dokumentRepo,
            brevService = brevService,
            personService = personService,
            identClient = clients.identClient,
        ).apply { addObserver(statistikkEventObserver) }

        val oppgaveService = OppgaveServiceImpl(
            oppgaveClient = clients.oppgaveClient,
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
            toggleService = toggleService,
            clock = clock,
        ).apply {
            addObserver(statistikkEventObserver)
        }
        val ferdigstillVedtakService = FerdigstillVedtakServiceImpl(
            brevService = brevService,
            oppgaveService = oppgaveService,
            vedtakRepo = databaseRepos.vedtakRepo,
            behandlingMetrics = behandlingMetrics,
        )

        val vedtakService = VedtakServiceImpl(
            vedtakRepo = databaseRepos.vedtakRepo,
        )

        val tilbakekrevingService = TilbakekrevingServiceImpl(
            tilbakekrevingRepo = databaseRepos.tilbakekrevingRepo,
            tilbakekrevingClient = clients.tilbakekrevingClient,
            vedtakService = vedtakService,
            brevService = brevService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
        )

        val stansAvYtelseService = StansYtelseServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            vedtakService = vedtakService,
            sakService = sakService,
            clock = clock,
            sessionFactory = databaseRepos.sessionFactory,
        ).apply { addObserver(statistikkEventObserver) }

        val kontrollsamtaleSetup = KontrollsamtaleSetup.create(
            sakService = sakService,
            personService = personService,
            brevService = brevService,
            oppgaveService = oppgaveService,
            sessionFactory = databaseRepos.sessionFactory as PostgresSessionFactory,
            dbMetrics = dbMetrics,
            clock = clock,
            serviceUser = applicationConfig.serviceUser.username,
            jobContextPostgresRepo = JobContextPostgresRepo(
                // TODO jah: Finnes nå 2 instanser av denne. Opprettes også i DatabaseBuilder for StønadsperiodePostgresRepo
                sessionFactory = databaseRepos.sessionFactory as PostgresSessionFactory,
            ),
            journalpostClient = clients.journalpostClient,
            stansAvYtelseService = stansAvYtelseService,
        )

        val revurderingService = RevurderingServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            identClient = clients.identClient,
            brevService = brevService,
            clock = clock,
            vedtakRepo = databaseRepos.vedtakRepo,
            sessionFactory = databaseRepos.sessionFactory,
            formuegrenserFactory = satsFactory.formuegrenserFactory,
            sakService = sakService,
            tilbakekrevingService = tilbakekrevingService,
            satsFactory = satsFactory,
            annullerKontrollsamtaleService = kontrollsamtaleSetup.annullerKontrollsamtaleService,
        ).apply { addObserver(statistikkEventObserver) }

        val gjenopptakAvYtelseService = GjenopptaYtelseServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            clock = clock,
            vedtakRepo = databaseRepos.vedtakRepo,
            sakService = sakService,
            sessionFactory = databaseRepos.sessionFactory,
        ).apply { addObserver(statistikkEventObserver) }

        val reguleringService = ReguleringServiceImpl(
            reguleringRepo = databaseRepos.reguleringRepo,
            sakRepo = databaseRepos.sak,
            utbetalingService = utbetalingService,
            vedtakService = vedtakService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
            tilbakekrevingService = tilbakekrevingService,
            satsFactory = satsFactory,
        ).apply { addObserver(statistikkEventObserver) }

        val nøkkelTallService = NøkkeltallServiceImpl(databaseRepos.nøkkeltallRepo)

        val søknadsbehandlingService = SøknadsbehandlingServiceImpl(
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            utbetalingService = utbetalingService,
            personService = personService,
            oppgaveService = oppgaveService,
            behandlingMetrics = behandlingMetrics,
            brevService = brevService,
            clock = clock,
            sakService = sakService,
            tilbakekrevingService = tilbakekrevingService,
            formuegrenserFactory = satsFactory.formuegrenserFactory,
            satsFactory = satsFactory,
        ).apply {
            addObserver(statistikkEventObserver)
        }
        val klageService = KlageServiceImpl(
            sakRepo = databaseRepos.sak,
            klageRepo = databaseRepos.klageRepo,
            vedtakService = vedtakService,
            brevService = brevService,
            personService = personService,
            identClient = clients.identClient,
            klageClient = clients.klageClient,
            sessionFactory = databaseRepos.sessionFactory,
            oppgaveService = oppgaveService,
            journalpostClient = clients.journalpostClient,
            clock = clock,
        ).apply { addObserver(statistikkEventObserver) }
        val klageinstanshendelseService = KlageinstanshendelseServiceImpl(
            klageinstanshendelseRepo = databaseRepos.klageinstanshendelseRepo,
            klageRepo = databaseRepos.klageRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
        )
        val skatteServiceImpl = SkatteServiceImpl(skatteClient = clients.skatteOppslag, clock = clock)
        val iverksettSøknadsbehandlingService = IverksettSøknadsbehandlingServiceImpl(
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            utbetalingService = utbetalingService,
            brevService = brevService,
            clock = clock,
            vedtakRepo = databaseRepos.vedtakRepo,
            ferdigstillVedtakService = ferdigstillVedtakService,
            sakService = sakService,
            opprettPlanlagtKontrollsamtaleService = kontrollsamtaleSetup.opprettPlanlagtKontrollsamtaleService,
            sessionFactory = databaseRepos.sessionFactory,
        )
        return Services(
            avstemming = AvstemmingServiceImpl(
                repo = databaseRepos.avstemming,
                publisher = clients.avstemmingPublisher,
                clock = clock,
            ),
            utbetaling = utbetalingService,
            sak = sakService,
            søknad = søknadService,
            brev = brevService,
            lukkSøknad = LukkSøknadServiceImpl(
                søknadService = søknadService,
                brevService = brevService,
                oppgaveService = oppgaveService,
                personService = personService,
                søknadsbehandlingService = søknadsbehandlingService,
                identClient = clients.identClient,
                sakService = sakService,
                clock = clock,
                sessionFactory = databaseRepos.sessionFactory,
            ).apply {
                addObserver(statistikkEventObserver)
            },
            oppgave = oppgaveService,
            person = personService,
            toggles = toggleService,
            søknadsbehandling = SøknadsbehandlingServices(
                søknadsbehandlingService = søknadsbehandlingService,
                iverksettSøknadsbehandlingService = iverksettSøknadsbehandlingService,
            ),
            ferdigstillVedtak = ferdigstillVedtakService,
            revurdering = revurderingService,
            vedtakService = vedtakService,
            nøkkeltallService = nøkkelTallService,
            avslåSøknadManglendeDokumentasjonService = AvslåSøknadManglendeDokumentasjonServiceImpl(
                clock = clock,
                sakService = sakService,
                satsFactory = satsFactory,
                iverksettSøknadsbehandlingService = iverksettSøknadsbehandlingService,
                utbetalingService = utbetalingService,
                brevService = brevService,
            ),
            klageService = klageService,
            klageinstanshendelseService = klageinstanshendelseService,
            reguleringService = reguleringService,
            tilbakekrevingService = tilbakekrevingService,
            sendPåminnelserOmNyStønadsperiodeService = SendPåminnelserOmNyStønadsperiodeServiceImpl(
                clock = clock,
                sakRepo = databaseRepos.sak,
                sessionFactory = databaseRepos.sessionFactory,
                brevService = brevService,
                personService = personService,
                sendPåminnelseNyStønadsperiodeJobRepo = databaseRepos.sendPåminnelseNyStønadsperiodeJobRepo,
                formuegrenserFactory = satsFactory.formuegrenserFactory,
            ),
            skatteService = skatteServiceImpl,
            stansYtelse = stansAvYtelseService,
            gjenopptaYtelse = gjenopptakAvYtelseService,
            kontrollsamtaleSetup = kontrollsamtaleSetup,
        )
    }
}
