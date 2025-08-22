package no.nav.su.se.bakover.web.services

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.database.jobcontext.JobContextPostgresRepo
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.setup.KontrollsamtaleSetup
import no.nav.su.se.bakover.service.SendPåminnelserOmNyStønadsperiodeServiceImpl
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.brev.BrevServiceImpl
import no.nav.su.se.bakover.service.klage.KlageServiceImpl
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseServiceImpl
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallServiceImpl
import no.nav.su.se.bakover.service.oppgave.OppgaveServiceImpl
import no.nav.su.se.bakover.service.person.PersonServiceImpl
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.revurdering.GjenopptaYtelseServiceImpl
import no.nav.su.se.bakover.service.revurdering.RevurderingServiceImpl
import no.nav.su.se.bakover.service.revurdering.StansYtelseServiceImpl
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.skatt.JournalførSkattDokumentService
import no.nav.su.se.bakover.service.skatt.SkattDokumentServiceImpl
import no.nav.su.se.bakover.service.skatt.SkatteServiceImpl
import no.nav.su.se.bakover.service.statistikk.ResendStatistikkhendelserServiceImpl
import no.nav.su.se.bakover.service.statistikk.StønadStatistikkJobService
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonServiceImpl
import no.nav.su.se.bakover.service.søknad.SøknadServiceImpl
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.IverksettSøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import no.nav.su.se.bakover.vedtak.application.FerdigstillVedtakServiceImpl
import no.nav.su.se.bakover.vedtak.application.VedtakServiceImpl
import satser.domain.SatsFactory
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.application.utbetaling.UtbetalingServiceImpl
import java.time.Clock

data object ServiceBuilder {
    fun build(
        databaseRepos: DatabaseRepos,
        clients: Clients,
        clock: Clock,
        satsFactory: SatsFactory,
        formuegrenserFactory: FormuegrenserFactory,
        applicationConfig: ApplicationConfig,
        dbMetrics: DbMetrics,
    ): Services {
        val personService = PersonServiceImpl(
            personOppslag = clients.personOppslag,
            personRepo = databaseRepos.person,
        )

        val statistikkEventObserver = StatistikkEventObserverBuilder(
            kafkaPublisher = clients.kafkaPublisher,
            personService = personService,
            clock = clock,
            gitCommit = applicationConfig.gitCommit,
            stønadStatistikkRepo = databaseRepos.stønadStatistikkRepo,
        ).statistikkService
        val utbetalingService = UtbetalingServiceImpl(
            utbetalingRepo = databaseRepos.utbetaling,
            simuleringClient = clients.simuleringClient,
            utbetalingPublisher = clients.utbetalingPublisher,
        )
        val brevService = BrevServiceImpl(
            pdfGenerator = clients.pdfGenerator,
            dokumentRepo = databaseRepos.dokumentRepo,
            personService = personService,
            identClient = clients.identClient,
            clock = clock,
        )
        val sakService = SakServiceImpl(
            sakRepo = databaseRepos.sak,
            clock = clock,
            dokumentRepo = databaseRepos.dokumentRepo,
            brevService = brevService,
            clients.queryJournalpostClient,
            personService = personService,
        ).apply { addObserver(statistikkEventObserver) }

        val oppgaveService = OppgaveServiceImpl(
            oppgaveClient = clients.oppgaveClient,
        )
        val søknadService = SøknadServiceImpl(
            søknadRepo = databaseRepos.søknad,
            sakService = sakService,
            sakFactory = SakFactory(clock = clock),
            pdfGenerator = clients.pdfGenerator,
            journalførSøknadClient = clients.journalførClients.søknad,
            personService = personService,
            oppgaveService = oppgaveService,
            clock = clock,
        ).apply {
            addObserver(statistikkEventObserver)
        }

        val skattDokumentService = SkattDokumentServiceImpl(
            pdfGenerator = clients.pdfGenerator,
            personService = personService,
            dokumentSkattRepo = databaseRepos.dokumentSkattRepo,
            journalførSkattDokumentService = JournalførSkattDokumentService(
                journalførSkattedokumentPåSakClient = clients.journalførClients.skattedokumentPåSak,
                journalførSkattedokumentUtenforSakClient = clients.journalførClients.skattedokumentUtenforSak,
                sakService = sakService,
                dokumentSkattRepo = databaseRepos.dokumentSkattRepo,
            ),
            clock = clock,
        )

        val skatteServiceImpl = SkatteServiceImpl(
            skatteClient = clients.skatteOppslag,
            skattDokumentService = skattDokumentService,
            personService = personService,
            sakService = sakService,
            journalpostClient = clients.queryJournalpostClient,
            clock = clock,
            isProd = applicationConfig.naisCluster == ApplicationConfig.NaisCluster.Prod,
        )

        val søknadsbehandlingService = SøknadsbehandlingServiceImpl(
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            utbetalingService = utbetalingService,
            personService = personService,
            oppgaveService = oppgaveService,
            brevService = brevService,
            clock = clock,
            sakService = sakService,
            formuegrenserFactory = formuegrenserFactory,
            satsFactory = satsFactory,
            sessionFactory = databaseRepos.sessionFactory,
            skatteService = skatteServiceImpl,
        ).apply {
            addObserver(statistikkEventObserver)
        }

        val vedtakService = VedtakServiceImpl(
            vedtakRepo = databaseRepos.vedtakRepo,
            sakService = sakService,
            oppgaveService = oppgaveService,
            søknadsbehandlingService = søknadsbehandlingService,
            clock = clock,
        ).apply {
            addObserver(statistikkEventObserver)
        }
        val ferdigstillVedtakService = FerdigstillVedtakServiceImpl(
            brevService = brevService,
            oppgaveService = oppgaveService,
            vedtakService = vedtakService,
            clock = clock,
            satsFactory = satsFactory,
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
            queryJournalpostClient = clients.queryJournalpostClient,
            stansAvYtelseService = stansAvYtelseService,
            personService = personService,
        )

        val revurderingService = RevurderingServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            brevService = brevService,
            clock = clock,
            vedtakService = vedtakService,
            sessionFactory = databaseRepos.sessionFactory,
            formuegrenserFactory = formuegrenserFactory,
            sakService = sakService,
            satsFactory = satsFactory,
            annullerKontrollsamtaleService = kontrollsamtaleSetup.annullerKontrollsamtaleService,
        ).apply { addObserver(statistikkEventObserver) }

        val gjenopptakAvYtelseService = GjenopptaYtelseServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            clock = clock,
            vedtakService = vedtakService,
            sakService = sakService,
            sessionFactory = databaseRepos.sessionFactory,
        ).apply { addObserver(statistikkEventObserver) }

        val reguleringService = ReguleringServiceImpl(
            reguleringRepo = databaseRepos.reguleringRepo,
            sakService = sakService,
            utbetalingService = utbetalingService,
            vedtakService = vedtakService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
            satsFactory = satsFactory,
        ).apply { addObserver(statistikkEventObserver) }

        val nøkkelTallService = NøkkeltallServiceImpl(databaseRepos.nøkkeltallRepo)

        val klageService = KlageServiceImpl(
            sakService = sakService,
            klageRepo = databaseRepos.klageRepo,
            vedtakService = vedtakService,
            brevService = brevService,
            klageClient = clients.klageClient,
            sessionFactory = databaseRepos.sessionFactory,
            oppgaveService = oppgaveService,
            queryJournalpostClient = clients.queryJournalpostClient,
            clock = clock,
            dokumentHendelseRepo = databaseRepos.dokumentHendelseRepo,
        ).apply { addObserver(statistikkEventObserver) }
        val klageinstanshendelseService = KlageinstanshendelseServiceImpl(
            klageinstanshendelseRepo = databaseRepos.klageinstanshendelseRepo,
            klageRepo = databaseRepos.klageRepo,
            oppgaveService = oppgaveService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
        )
        val iverksettSøknadsbehandlingService = IverksettSøknadsbehandlingServiceImpl(
            sakService = sakService,
            clock = clock,
            utbetalingService = utbetalingService,
            sessionFactory = databaseRepos.sessionFactory,
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            vedtakService = vedtakService,
            opprettPlanlagtKontrollsamtaleService = kontrollsamtaleSetup.opprettPlanlagtKontrollsamtaleService,
            ferdigstillVedtakService = ferdigstillVedtakService,
            brevService = brevService,
            skattDokumentService = skattDokumentService,
            satsFactory = satsFactory,
        ).apply { addObserver(statistikkEventObserver) }
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
                søknadsbehandlingService = søknadsbehandlingService,
                sakService = sakService,
                sessionFactory = databaseRepos.sessionFactory,
            ).apply {
                addObserver(statistikkEventObserver)
            },
            oppgave = oppgaveService,
            person = personService,
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
                formuegrenserFactory = formuegrenserFactory,
                iverksettSøknadsbehandlingService = iverksettSøknadsbehandlingService,
                utbetalingService = utbetalingService,
                brevService = brevService,
                oppgaveService = oppgaveService,
            ),
            klageService = klageService,
            klageinstanshendelseService = klageinstanshendelseService,
            reguleringService = reguleringService,
            sendPåminnelserOmNyStønadsperiodeService = SendPåminnelserOmNyStønadsperiodeServiceImpl(
                clock = clock,
                sakService = sakService,
                sessionFactory = databaseRepos.sessionFactory,
                brevService = brevService,
                sendPåminnelseNyStønadsperiodeJobRepo = databaseRepos.sendPåminnelseNyStønadsperiodeJobRepo,
                formuegrenserFactory = formuegrenserFactory,
                personService = personService,
            ),
            skatteService = skatteServiceImpl,
            stansYtelse = stansAvYtelseService,
            gjenopptaYtelse = gjenopptakAvYtelseService,
            kontrollsamtaleSetup = kontrollsamtaleSetup,
            resendStatistikkhendelserService = ResendStatistikkhendelserServiceImpl(
                vedtakService = vedtakService,
                sakRepo = databaseRepos.sak,
                statistikkEventObserver = statistikkEventObserver,
            ),
            personhendelseService = PersonhendelseServiceImpl(
                sakRepo = databaseRepos.sak,
                personhendelseRepo = databaseRepos.personhendelseRepo,
                vedtakService = vedtakService,
                oppgaveServiceImpl = oppgaveService,
                clock = clock,
            ),
            stønadStatistikkJobService = StønadStatistikkJobService(
                stønadStatistikkRepo = databaseRepos.stønadStatistikkRepo,
            ),
        )
    }
}
