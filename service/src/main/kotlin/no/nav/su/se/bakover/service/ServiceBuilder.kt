package no.nav.su.se.bakover.service

import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.brev.BrevServiceImpl
import no.nav.su.se.bakover.service.klage.KlageServiceImpl
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseServiceImpl
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleServiceImpl
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallServiceImpl
import no.nav.su.se.bakover.service.oppgave.OppgaveServiceImpl
import no.nav.su.se.bakover.service.person.PersonServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.revurdering.RevurderingServiceImpl
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.skatt.SkatteServiceImpl
import no.nav.su.se.bakover.service.statistikk.StatistikkServiceImpl
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonServiceImpl
import no.nav.su.se.bakover.service.søknad.SøknadServiceImpl
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingServiceImpl
import no.nav.su.se.bakover.service.toggles.ToggleServiceImpl
import no.nav.su.se.bakover.service.utbetaling.UtbetalingServiceImpl
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakServiceImpl
import no.nav.su.se.bakover.service.vedtak.VedtakServiceImpl
import java.time.Clock

object ServiceBuilder {
    fun build(
        // TODO jah: TDD-messig bør denne service-laget ha sin egen versjon av denne dataclassen som kun refererer til interfacene (som bør ligge i domain/service)
        databaseRepos: DatabaseRepos,
        clients: Clients,
        behandlingMetrics: BehandlingMetrics,
        søknadMetrics: SøknadMetrics,
        clock: Clock,
        unleash: Unleash,
        satsFactory: SatsFactory,
    ): Services {
        val personService = PersonServiceImpl(clients.personOppslag)
        val toggleService = ToggleServiceImpl(unleash)
        val statistikkService = StatistikkServiceImpl(
            publisher = clients.kafkaPublisher,
            personService = personService,
            sakRepo = databaseRepos.sak,
            vedtakRepo = databaseRepos.vedtakRepo,
            clock = clock,
        )
        val sakService = SakServiceImpl(
            sakRepo = databaseRepos.sak,
            clock = clock,
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
            dokDistFordeling = clients.dokDistFordeling,
            dokumentRepo = databaseRepos.dokumentRepo,
            sakService = sakService,
            personService = personService,
            sessionFactory = databaseRepos.sessionFactory,
            microsoftGraphApiOppslag = clients.identClient,
            utbetalingService = utbetalingService,
            clock = clock,
            satsFactory = satsFactory,
        )
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
            addObserver(statistikkService)
        }
        val ferdigstillVedtakService = FerdigstillVedtakServiceImpl(
            brevService = brevService,
            oppgaveService = oppgaveService,
            vedtakRepo = databaseRepos.vedtakRepo,
            behandlingMetrics = behandlingMetrics,
        )

        val vedtakService = VedtakServiceImpl(
            vedtakRepo = databaseRepos.vedtakRepo,
            sakService = sakService,
            clock = clock,
        )

        val kontrollsamtaleService = KontrollsamtaleServiceImpl(
            sakService = sakService,
            personService = personService,
            brevService = brevService,
            oppgaveService = oppgaveService,
            kontrollsamtaleRepo = databaseRepos.kontrollsamtaleRepo,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
        )

        val tilbakekrevingService = TilbakekrevingServiceImpl(
            tilbakekrevingRepo = databaseRepos.tilbakekrevingRepo,
            tilbakekrevingClient = clients.tilbakekrevingClient,
            vedtakService = vedtakService,
            brevService = brevService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
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
            vedtakService = vedtakService,
            kontrollsamtaleService = kontrollsamtaleService,
            sessionFactory = databaseRepos.sessionFactory,
            formuegrenserFactory = satsFactory.formuegrenserFactory,
            sakService = sakService,
            avkortingsvarselRepo = databaseRepos.avkortingsvarselRepo,
            toggleService = toggleService,
            tilbakekrevingService = tilbakekrevingService,
            satsFactory = satsFactory,
        ).apply { addObserver(statistikkService) }

        val reguleringService = ReguleringServiceImpl(
            reguleringRepo = databaseRepos.reguleringRepo,
            sakRepo = databaseRepos.sak,
            utbetalingService = utbetalingService,
            vedtakService = vedtakService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
            tilbakekrevingService = tilbakekrevingService,
            satsFactory = satsFactory,
        ).apply { addObserver(statistikkService) }

        val nøkkelTallService = NøkkeltallServiceImpl(databaseRepos.nøkkeltallRepo)

        val søknadsbehandlingService = SøknadsbehandlingServiceImpl(
            søknadService = søknadService,
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            utbetalingService = utbetalingService,
            personService = personService,
            oppgaveService = oppgaveService,
            behandlingMetrics = behandlingMetrics,
            brevService = brevService,
            clock = clock,
            vedtakRepo = databaseRepos.vedtakRepo,
            ferdigstillVedtakService = ferdigstillVedtakService,
            sakService = sakService,
            kontrollsamtaleService = kontrollsamtaleService,
            sessionFactory = databaseRepos.sessionFactory,
            avkortingsvarselRepo = databaseRepos.avkortingsvarselRepo,
            tilbakekrevingService = tilbakekrevingService,
            formuegrenserFactory = satsFactory.formuegrenserFactory,
            satsFactory = satsFactory,
        ).apply {
            addObserver(statistikkService)
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
        ).apply { addObserver(statistikkService) }
        val klageinstanshendelseService = KlageinstanshendelseServiceImpl(
            klageinstanshendelseRepo = databaseRepos.klageinstanshendelseRepo,
            klageRepo = databaseRepos.klageRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
        )
        val skatteServiceImpl =
            SkatteServiceImpl(skatteClient = clients.skatteOppslag, maskinportenClient = clients.maskinportenClient)
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
                addObserver(statistikkService)
            },
            oppgave = oppgaveService,
            person = personService,
            statistikk = statistikkService,
            toggles = toggleService,
            søknadsbehandling = søknadsbehandlingService,
            ferdigstillVedtak = ferdigstillVedtakService,
            revurdering = revurderingService,
            vedtakService = vedtakService,
            nøkkeltallService = nøkkelTallService,
            avslåSøknadManglendeDokumentasjonService = AvslåSøknadManglendeDokumentasjonServiceImpl(
                clock = clock,
                søknadsbehandlingService = søknadsbehandlingService,
                vedtakService = vedtakService,
                oppgaveService = oppgaveService,
                brevService = brevService,
                sessionFactory = databaseRepos.sessionFactory,
                sakService = sakService,
                satsFactory = satsFactory,
            ),
            kontrollsamtale = kontrollsamtaleService,
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
                jobContextRepo = databaseRepos.jobContextRepo,
                formuegrenserFactory = satsFactory.formuegrenserFactory,
            ),
            skatteService = skatteServiceImpl,
        )
    }
}
