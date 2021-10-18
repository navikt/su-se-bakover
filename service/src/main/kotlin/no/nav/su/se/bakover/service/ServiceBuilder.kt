package no.nav.su.se.bakover.service

import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.brev.BrevServiceImpl
import no.nav.su.se.bakover.service.grunnlag.GrunnlagServiceImpl
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingServiceImpl
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallServiceImpl
import no.nav.su.se.bakover.service.oppgave.OppgaveServiceImpl
import no.nav.su.se.bakover.service.person.PersonServiceImpl
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
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
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
            microsoftGraphApiOppslag = clients.microsoftGraphApiClient,
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
            personService = personService,
            utbetalingService = utbetalingService,
            microsoftGraphApiOppslag = clients.microsoftGraphApiClient,
            clock = clock,
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

        val revurderingService = RevurderingServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            microsoftGraphApiClient = clients.microsoftGraphApiClient,
            brevService = brevService,
            clock = clock,
            vedtakRepo = databaseRepos.vedtakRepo,
            vilkårsvurderingService = vilkårsvurderingService,
            grunnlagService = grunnlagService,
            vedtakService = vedtakService,
            sakService = sakService,
        ).apply { addObserver(statistikkService) }

        val nøkkelTallService = NøkkeltallServiceImpl(databaseRepos.nøkkeltallRepo)

        val opprettVedtakssnapshotService = OpprettVedtakssnapshotService(databaseRepos.vedtakssnapshot)

        val toggleService = ToggleServiceImpl(unleash)

        val søknadsbehandlingService = SøknadsbehandlingServiceImpl(
            søknadService = søknadService,
            søknadRepo = databaseRepos.søknad,
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            utbetalingService = utbetalingService,
            personService = personService,
            oppgaveService = oppgaveService,
            behandlingMetrics = behandlingMetrics,
            microsoftGraphApiClient = clients.microsoftGraphApiClient,
            brevService = brevService,
            opprettVedtakssnapshotService = opprettVedtakssnapshotService,
            clock = clock,
            vedtakRepo = databaseRepos.vedtakRepo,
            ferdigstillVedtakService = ferdigstillVedtakService,
            vilkårsvurderingService = vilkårsvurderingService,
            grunnlagService = grunnlagService,
            sakService = sakService,
        ).apply {
            addObserver(statistikkService)
        }
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
                microsoftGraphApiClient = clients.microsoftGraphApiClient,
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
        )
    }
}
