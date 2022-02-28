package no.nav.su.se.bakover.service

import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.brev.BrevServiceImpl
import no.nav.su.se.bakover.service.grunnlag.GrunnlagServiceImpl
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingServiceImpl
import no.nav.su.se.bakover.service.klage.KlageServiceImpl
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseServiceImpl
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleServiceImpl
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallServiceImpl
import no.nav.su.se.bakover.service.oppgave.OppgaveServiceImpl
import no.nav.su.se.bakover.service.person.PersonServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.revurdering.RevurderingServiceImpl
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.statistikk.StatistikkServiceImpl
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonServiceImpl
import no.nav.su.se.bakover.service.søknad.SøknadServiceImpl
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServiceImpl
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
    ): Services {
        val personService = PersonServiceImpl(clients.personOppslag)
        val statistikkService = StatistikkServiceImpl(
            clients.kafkaPublisher,
            personService,
            databaseRepos.sak,
            databaseRepos.vedtakRepo,
            clock,
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

        val grunnlagService = GrunnlagServiceImpl(
            grunnlagRepo = databaseRepos.grunnlagRepo,
        )

        val vilkårsvurderingService = VilkårsvurderingServiceImpl(
            uføreVilkårsvurderingRepo = databaseRepos.uføreVilkårsvurderingRepo,
            formueVilkårsvurderingRepo = databaseRepos.formueVilkårsvurderingRepo,
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
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
            kontrollsamtaleRepo = databaseRepos.kontrollsamtaleRepo,
        )

        val toggleService = ToggleServiceImpl(unleash)
        val revurderingService = RevurderingServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            identClient = clients.identClient,
            brevService = brevService,
            clock = clock,
            vedtakRepo = databaseRepos.vedtakRepo,
            vilkårsvurderingService = vilkårsvurderingService,
            grunnlagService = grunnlagService,
            vedtakService = vedtakService,
            sakService = sakService,
            kontrollsamtaleService = kontrollsamtaleService,
            sessionFactory = databaseRepos.sessionFactory,
            avkortingsvarselRepo = databaseRepos.avkortingsvarselRepo,
            toggleService = toggleService,
        ).apply { addObserver(statistikkService) }

        val reguleringService = ReguleringServiceImpl(
            reguleringRepo = databaseRepos.reguleringRepo,
            sakRepo = databaseRepos.sak,
            utbetalingService = utbetalingService,
            vedtakService = vedtakService,
            vilkårsvurderingService = vilkårsvurderingService,
            grunnlagService = grunnlagService,
            clock = clock,
        )

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
            grunnlagService = grunnlagService,
            sakService = sakService,
            kontrollsamtaleService = kontrollsamtaleService,
            sessionFactory = databaseRepos.sessionFactory,
            avkortingsvarselRepo = databaseRepos.avkortingsvarselRepo,
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
        )
        val klageinstanshendelseService = KlageinstanshendelseServiceImpl(
            klageinstanshendelseRepo = databaseRepos.klageinstanshendelseRepo,
            klageRepo = databaseRepos.klageRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
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
            grunnlagService = grunnlagService,
            nøkkeltallService = nøkkelTallService,
            avslåSøknadManglendeDokumentasjonService = AvslåSøknadManglendeDokumentasjonServiceImpl(
                clock = clock,
                søknadsbehandlingService = søknadsbehandlingService,
                vedtakService = vedtakService,
                oppgaveService = oppgaveService,
                brevService = brevService,
                sessionFactory = databaseRepos.sessionFactory,
                sakService = sakService,
            ),
            kontrollsamtale = kontrollsamtaleService,
            klageService = klageService,
            klageinstanshendelseService = klageinstanshendelseService,
            reguleringService = reguleringService,
        )
    }
}
