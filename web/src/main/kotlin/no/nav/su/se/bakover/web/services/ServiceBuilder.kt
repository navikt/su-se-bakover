package no.nav.su.se.bakover.web.services

import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.database.jobcontext.JobContextPostgresRepo
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstServiceImpl
import no.nav.su.se.bakover.domain.mottaker.MottakerServiceImpl
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.regulering.ReguleringAutomatiskService
import no.nav.su.se.bakover.domain.regulering.ReguleringManuellService
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.kontrollsamtale.application.KontrollsamtaleDriftOversiktServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.setup.KontrollsamtaleSetup
import no.nav.su.se.bakover.service.SendPåminnelserOmNyStønadsperiodeServiceImpl
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.brev.BrevServiceImpl
import no.nav.su.se.bakover.service.klage.JournalpostAdresseServiceImpl
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KlageServiceImpl
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseServiceImpl
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallServiceImpl
import no.nav.su.se.bakover.service.oppgave.OppgaveServiceImpl
import no.nav.su.se.bakover.service.person.PersonServiceImpl
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringAutomatiskServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringHentEksterneReguleringerService
import no.nav.su.se.bakover.service.regulering.ReguleringManuellServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.revurdering.GjenopptaYtelseServiceImpl
import no.nav.su.se.bakover.service.revurdering.RevurderingServiceImpl
import no.nav.su.se.bakover.service.revurdering.StansYtelseServiceImpl
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.skatt.JournalførSkattDokumentService
import no.nav.su.se.bakover.service.skatt.SkattDokumentService
import no.nav.su.se.bakover.service.skatt.SkattDokumentServiceImpl
import no.nav.su.se.bakover.service.skatt.SkatteServiceImpl
import no.nav.su.se.bakover.service.statistikk.FritekstAvslagServiceImpl
import no.nav.su.se.bakover.service.statistikk.ResendStatistikkhendelserServiceImpl
import no.nav.su.se.bakover.service.statistikk.SakStatistikkBigQueryService
import no.nav.su.se.bakover.service.statistikk.SakStatistikkBigQueryServiceImpl
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import no.nav.su.se.bakover.service.statistikk.StønadStatistikkJobServiceImpl
import no.nav.su.se.bakover.service.statistikk.SøknadStatistikkServiceImpl
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonServiceImpl
import no.nav.su.se.bakover.service.søknad.SøknadServiceImpl
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.IverksettSøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import no.nav.su.se.bakover.vedtak.application.FerdigstillVedtakServiceImpl
import no.nav.su.se.bakover.vedtak.application.VedtakServiceImpl
import no.nav.su.se.bakover.web.services.aap.AapJobServiceImpl
import no.nav.su.se.bakover.web.services.pesys.PesysJobServiceImpl
import person.domain.PersonService
import satser.domain.SatsFactory
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.skatt.application.SkatteService
import økonomi.application.utbetaling.UtbetalingService
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
        sakStatistikkRepo: SakStatistikkRepo,
    ): Services {
        val kjerneTjenester = buildKjerneTjenester(
            databaseRepos = databaseRepos,
            clients = clients,
            clock = clock,
            applicationConfig = applicationConfig,
            sakStatistikkRepo = sakStatistikkRepo,
        )
        val søknadService = buildSøknadService(
            databaseRepos = databaseRepos,
            clients = clients,
            clock = clock,
            kjerneTjenester = kjerneTjenester,
        )
        val skatteServices = buildSkattServices(
            databaseRepos = databaseRepos,
            clients = clients,
            clock = clock,
            kjerneTjenester = kjerneTjenester,
        )
        val søknadsbehandlingService = buildSøknadsbehandlingService(
            databaseRepos = databaseRepos,
            kjerneTjenester = kjerneTjenester,
            skatteService = skatteServices.skatteService,
            formuegrenserFactory = formuegrenserFactory,
            satsFactory = satsFactory,
            clock = clock,
        )
        val vedtakService = buildVedtakService(
            databaseRepos = databaseRepos,
            kjerneTjenester = kjerneTjenester,
            søknadsbehandlingService = søknadsbehandlingService,
            clock = clock,
            sakStatistikkRepo = sakStatistikkRepo,
        )
        val mottakerService = buildMottakerService(databaseRepos)
        val ferdigstillVedtakService = buildFerdigstillVedtakService(
            kjerneTjenester = kjerneTjenester,
            vedtakService = vedtakService,
            mottakerService = mottakerService,
            satsFactory = satsFactory,
            clock = clock,
        )
        val stansAvYtelseService = buildStansYtelseService(
            databaseRepos = databaseRepos,
            kjerneTjenester = kjerneTjenester,
            vedtakService = vedtakService,
            clock = clock,
        )
        val postgresSessionFactory = databaseRepos.requirePostgresSessionFactory()
        val kontrollsamtaleSetup = buildKontrollsamtaleSetup(
            kjerneTjenester = kjerneTjenester,
            stansAvYtelseService = stansAvYtelseService,
            applicationConfig = applicationConfig,
            clients = clients,
            dbMetrics = dbMetrics,
            clock = clock,
            sessionFactory = postgresSessionFactory,
        )
        val revurderingService = buildRevurderingService(
            databaseRepos = databaseRepos,
            kjerneTjenester = kjerneTjenester,
            vedtakService = vedtakService,
            mottakerService = mottakerService,
            kontrollsamtaleSetup = kontrollsamtaleSetup,
            formuegrenserFactory = formuegrenserFactory,
            satsFactory = satsFactory,
            clock = clock,
        )
        val gjenopptaYtelseService = buildGjenopptaYtelseService(
            databaseRepos = databaseRepos,
            kjerneTjenester = kjerneTjenester,
            vedtakService = vedtakService,
            clock = clock,
        )
        val reguleringServices = buildReguleringServices(
            databaseRepos = databaseRepos,
            kjerneTjenester = kjerneTjenester,
            vedtakService = vedtakService,
            satsFactory = satsFactory,
            clients = clients,
            clock = clock,
        )
        val journalpostAdresseService = JournalpostAdresseServiceImpl(
            klageRepo = databaseRepos.klageRepo,
            journalpostClient = clients.queryJournalpostClient,
            dokumentRepo = databaseRepos.dokumentRepo,
        )
        val klageServices = buildKlageServices(
            databaseRepos = databaseRepos,
            clients = clients,
            kjerneTjenester = kjerneTjenester,
            vedtakService = vedtakService,
            clock = clock,
            mottakerService = mottakerService,
        )
        val iverksettSøknadsbehandlingService = buildIverksettSøknadsbehandlingService(
            databaseRepos = databaseRepos,
            kjerneTjenester = kjerneTjenester,
            vedtakService = vedtakService,
            kontrollsamtaleSetup = kontrollsamtaleSetup,
            ferdigstillVedtakService = ferdigstillVedtakService,
            skattDokumentService = skatteServices.skattDokumentService,
            satsFactory = satsFactory,
            clock = clock,
            mottakerService = mottakerService,
        )
        val lukkSøknadService = buildLukkSøknadService(
            databaseRepos = databaseRepos,
            kjerneTjenester = kjerneTjenester,
            søknadService = søknadService,
            søknadsbehandlingService = søknadsbehandlingService,
            clock = clock,
        )

        return Services(
            avstemming = AvstemmingServiceImpl(
                repo = databaseRepos.avstemming,
                publisher = clients.avstemmingPublisher,
                clock = clock,
            ),
            utbetaling = kjerneTjenester.utbetalingService,
            sak = kjerneTjenester.sakService,
            søknad = søknadService,
            brev = kjerneTjenester.brevService,
            fritekstService = kjerneTjenester.fritekstService,
            lukkSøknad = lukkSøknadService,
            oppgave = kjerneTjenester.oppgaveService,
            person = kjerneTjenester.personService,
            søknadsbehandling = SøknadsbehandlingServices(
                søknadsbehandlingService = søknadsbehandlingService,
                iverksettSøknadsbehandlingService = iverksettSøknadsbehandlingService,
            ),
            ferdigstillVedtak = ferdigstillVedtakService,
            revurdering = revurderingService,
            vedtakService = vedtakService,
            nøkkeltallService = NøkkeltallServiceImpl(databaseRepos.nøkkeltallRepo),
            avslåSøknadManglendeDokumentasjonService = AvslåSøknadManglendeDokumentasjonServiceImpl(
                clock = clock,
                sakService = kjerneTjenester.sakService,
                satsFactory = satsFactory,
                formuegrenserFactory = formuegrenserFactory,
                iverksettSøknadsbehandlingService = iverksettSøknadsbehandlingService,
                utbetalingService = kjerneTjenester.utbetalingService,
                brevService = kjerneTjenester.brevService,
                oppgaveService = kjerneTjenester.oppgaveService,
            ),
            klageService = klageServices.klageService,
            klageinstanshendelseService = klageServices.klageinstanshendelseService,
            journalpostAdresseService = journalpostAdresseService,
            reguleringManuellService = reguleringServices.reguleringManuellService,
            reguleringAutomatiskService = reguleringServices.reguleringAutomatiskService,
            sendPåminnelserOmNyStønadsperiodeService = SendPåminnelserOmNyStønadsperiodeServiceImpl(
                clock = clock,
                sakService = kjerneTjenester.sakService,
                sessionFactory = databaseRepos.sessionFactory,
                brevService = kjerneTjenester.brevService,
                sendPåminnelseNyStønadsperiodeJobRepo = databaseRepos.sendPåminnelseNyStønadsperiodeJobRepo,
                personService = kjerneTjenester.personService,
            ),
            skatteService = skatteServices.skatteService,
            stansYtelse = stansAvYtelseService,
            gjenopptaYtelse = gjenopptaYtelseService,
            kontrollsamtaleSetup = kontrollsamtaleSetup,
            resendStatistikkhendelserService = ResendStatistikkhendelserServiceImpl(
                vedtakService = vedtakService,
                sakRepo = databaseRepos.sak,
                statistikkEventObserver = kjerneTjenester.statistikkEventObserver,
            ),
            personhendelseService = PersonhendelseServiceImpl(
                sakRepo = databaseRepos.sak,
                personhendelseRepo = databaseRepos.personhendelseRepo,
                vedtakService = vedtakService,
                oppgaveServiceImpl = kjerneTjenester.oppgaveService,
                clock = clock,
            ),
            stønadStatistikkJobService = StønadStatistikkJobServiceImpl(
                stønadStatistikkRepo = databaseRepos.stønadStatistikkRepo,
                vedtakRepo = databaseRepos.vedtakRepo,
                sessionFactory = databaseRepos.sessionFactory,
                clock = clock,
            ),
            pesysJobService = PesysJobServiceImpl(client = clients.pesysklient),
            aapJobService = AapJobServiceImpl(client = clients.aapApiInternClient, clock = clock),
            sakstatistikkBigQueryService = kjerneTjenester.sakStatistikkBigQueryService,
            fritekstAvslagService = FritekstAvslagServiceImpl(databaseRepos.fritekstAvslagRepo),
            søknadStatistikkService = SøknadStatistikkServiceImpl(databaseRepos.søknadStatistikkRepo),
            mottakerService = mottakerService,
            kontrollsamtaleDriftOversiktService = KontrollsamtaleDriftOversiktServiceImpl(
                kontrollsamtaleService = kontrollsamtaleSetup.kontrollsamtaleService,
                utbetalingsRepo = databaseRepos.utbetaling,
                sakRepo = databaseRepos.sak,
            ),
        )
    }

    private data class KjerneTjenester(
        val personService: PersonService,
        val sakService: SakService,
        val oppgaveService: OppgaveService,
        val brevService: BrevService,
        val fritekstService: FritekstService,
        val utbetalingService: UtbetalingService,
        val sakStatistikkService: SakStatistikkService,
        val sakStatistikkBigQueryService: SakStatistikkBigQueryService,
        val statistikkEventObserver: StatistikkEventObserver,
    )

    private data class SkattServices(
        val skattDokumentService: SkattDokumentService,
        val skatteService: SkatteService,
    )

    private data class ReguleringServices(
        val reguleringManuellService: ReguleringManuellService,
        val reguleringAutomatiskService: ReguleringAutomatiskService,
    )

    private data class KlageServices(
        val klageService: KlageService,
        val klageinstanshendelseService: KlageinstanshendelseService,
    )

    private fun DatabaseRepos.requirePostgresSessionFactory(): PostgresSessionFactory {
        return sessionFactory as? PostgresSessionFactory
            ?: error("DatabaseRepos.sessionFactory må være PostgresSessionFactory, var ${sessionFactory::class.qualifiedName}")
    }

    private fun buildKjerneTjenester(
        databaseRepos: DatabaseRepos,
        clients: Clients,
        clock: Clock,
        applicationConfig: ApplicationConfig,
        sakStatistikkRepo: SakStatistikkRepo,
    ): KjerneTjenester {
        val personService = PersonServiceImpl(
            personOppslag = clients.personOppslag,
            personRepo = databaseRepos.person,
        )
        val sakStatistikkService = SakStatistikkService(sakStatistikkRepo, clock)
        val sakStatistikkBigQueryService = SakStatistikkBigQueryServiceImpl(databaseRepos.sakStatistikkRepo)
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
        )
        val brevService = BrevServiceImpl(
            pdfGenerator = clients.pdfGenerator,
            dokumentRepo = databaseRepos.dokumentRepo,
            personService = personService,
            identClient = clients.identClient,
            clock = clock,
        )
        val fritekstService = FritekstServiceImpl(
            repository = databaseRepos.fritekstRepo,
        )
        val sakService = SakServiceImpl(
            sakRepo = databaseRepos.sak,
            clock = clock,
            dokumentRepo = databaseRepos.dokumentRepo,
            brevService = brevService,
            journalpostClient = clients.queryJournalpostClient,
            personService = personService,
            fritekstService = fritekstService,
        ).apply { addObserver(statistikkEventObserver) }
        val oppgaveService = OppgaveServiceImpl(
            oppgaveClient = clients.oppgaveClient,
        )
        return KjerneTjenester(
            personService = personService,
            sakService = sakService,
            oppgaveService = oppgaveService,
            brevService = brevService,
            fritekstService = fritekstService,
            utbetalingService = utbetalingService,
            sakStatistikkService = sakStatistikkService,
            sakStatistikkBigQueryService = sakStatistikkBigQueryService,
            statistikkEventObserver = statistikkEventObserver,
        )
    }

    private fun buildSøknadService(
        databaseRepos: DatabaseRepos,
        clients: Clients,
        clock: Clock,
        kjerneTjenester: KjerneTjenester,
    ): SøknadServiceImpl {
        return SøknadServiceImpl(
            søknadRepo = databaseRepos.søknad,
            sakService = kjerneTjenester.sakService,
            sakFactory = SakFactory(clock = clock),
            pdfGenerator = clients.pdfGenerator,
            journalførSøknadClient = clients.journalførClients.søknad,
            personService = kjerneTjenester.personService,
            oppgaveService = kjerneTjenester.oppgaveService,
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            clock = clock,
            sessionFactory = databaseRepos.sessionFactory,
            sakStatistikkService = kjerneTjenester.sakStatistikkService,
        ).apply {
            addObserver(kjerneTjenester.statistikkEventObserver)
        }
    }

    private fun buildSkattServices(
        databaseRepos: DatabaseRepos,
        clients: Clients,
        clock: Clock,
        kjerneTjenester: KjerneTjenester,
    ): SkattServices {
        val skattDokumentService = SkattDokumentServiceImpl(
            pdfGenerator = clients.pdfGenerator,
            personService = kjerneTjenester.personService,
            dokumentSkattRepo = databaseRepos.dokumentSkattRepo,
            journalførSkattDokumentService = JournalførSkattDokumentService(
                journalførSkattedokumentPåSakClient = clients.journalførClients.skattedokumentPåSak,
                journalførSkattedokumentUtenforSakClient = clients.journalførClients.skattedokumentUtenforSak,
                sakService = kjerneTjenester.sakService,
                dokumentSkattRepo = databaseRepos.dokumentSkattRepo,
            ),
            clock = clock,
        )
        val skatteService = SkatteServiceImpl(
            skatteClient = clients.skatteOppslag,
            skattDokumentService = skattDokumentService,
            personService = kjerneTjenester.personService,
            sakService = kjerneTjenester.sakService,
            journalpostClient = clients.queryJournalpostClient,
            clock = clock,
        )
        return SkattServices(
            skattDokumentService = skattDokumentService,
            skatteService = skatteService,
        )
    }

    private fun buildSøknadsbehandlingService(
        databaseRepos: DatabaseRepos,
        kjerneTjenester: KjerneTjenester,
        skatteService: SkatteService,
        formuegrenserFactory: FormuegrenserFactory,
        satsFactory: SatsFactory,
        clock: Clock,
    ): SøknadsbehandlingServiceImpl {
        return SøknadsbehandlingServiceImpl(
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            utbetalingService = kjerneTjenester.utbetalingService,
            personService = kjerneTjenester.personService,
            oppgaveService = kjerneTjenester.oppgaveService,
            brevService = kjerneTjenester.brevService,
            clock = clock,
            sakService = kjerneTjenester.sakService,
            formuegrenserFactory = formuegrenserFactory,
            satsFactory = satsFactory,
            sessionFactory = databaseRepos.sessionFactory,
            skatteService = skatteService,
            fritekstService = kjerneTjenester.fritekstService,
            sakStatistikkService = kjerneTjenester.sakStatistikkService,
        ).apply {
            addObserver(kjerneTjenester.statistikkEventObserver)
        }
    }

    private fun buildVedtakService(
        databaseRepos: DatabaseRepos,
        kjerneTjenester: KjerneTjenester,
        søknadsbehandlingService: SøknadsbehandlingServiceImpl,
        clock: Clock,
        sakStatistikkRepo: SakStatistikkRepo,
    ): VedtakServiceImpl {
        return VedtakServiceImpl(
            vedtakRepo = databaseRepos.vedtakRepo,
            sakService = kjerneTjenester.sakService,
            oppgaveService = kjerneTjenester.oppgaveService,
            søknadsbehandlingService = søknadsbehandlingService,
            klageRepo = databaseRepos.klageRepo,
            clock = clock,
            sessionFactory = databaseRepos.sessionFactory,
            sakStatistikkRepo = sakStatistikkRepo,
        ).apply {
            addObserver(kjerneTjenester.statistikkEventObserver)
        }
    }

    private fun buildMottakerService(databaseRepos: DatabaseRepos): MottakerServiceImpl {
        return MottakerServiceImpl(
            databaseRepos.mottakerRepo,
            dokumentRepo = databaseRepos.dokumentRepo,
            vedtakRepo = databaseRepos.vedtakRepo,
        )
    }

    private fun buildFerdigstillVedtakService(
        kjerneTjenester: KjerneTjenester,
        vedtakService: VedtakServiceImpl,
        mottakerService: MottakerServiceImpl,
        satsFactory: SatsFactory,
        clock: Clock,
    ): FerdigstillVedtakServiceImpl {
        return FerdigstillVedtakServiceImpl(
            brevService = kjerneTjenester.brevService,
            oppgaveService = kjerneTjenester.oppgaveService,
            vedtakService = vedtakService,
            clock = clock,
            satsFactory = satsFactory,
            fritekstService = kjerneTjenester.fritekstService,
            mottakerService = mottakerService,
        )
    }

    private fun buildStansYtelseService(
        databaseRepos: DatabaseRepos,
        kjerneTjenester: KjerneTjenester,
        vedtakService: VedtakServiceImpl,
        clock: Clock,
    ): StansYtelseServiceImpl {
        return StansYtelseServiceImpl(
            utbetalingService = kjerneTjenester.utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            vedtakService = vedtakService,
            sakService = kjerneTjenester.sakService,
            clock = clock,
            sessionFactory = databaseRepos.sessionFactory,
            sakStatistikkService = kjerneTjenester.sakStatistikkService,
        ).apply { addObserver(kjerneTjenester.statistikkEventObserver) }
    }

    private fun buildKontrollsamtaleSetup(
        kjerneTjenester: KjerneTjenester,
        stansAvYtelseService: StansYtelseServiceImpl,
        applicationConfig: ApplicationConfig,
        clients: Clients,
        dbMetrics: DbMetrics,
        clock: Clock,
        sessionFactory: PostgresSessionFactory,
    ): KontrollsamtaleSetup {
        return KontrollsamtaleSetup.create(
            sakService = kjerneTjenester.sakService,
            brevService = kjerneTjenester.brevService,
            oppgaveService = kjerneTjenester.oppgaveService,
            sessionFactory = sessionFactory,
            dbMetrics = dbMetrics,
            clock = clock,
            serviceUser = applicationConfig.serviceUser.username,
            jobContextPostgresRepo = JobContextPostgresRepo(
                // TODO jah: Finnes nå 2 instanser av denne. Opprettes også i DatabaseBuilder for StønadsperiodePostgresRepo
                sessionFactory = sessionFactory,
            ),
            queryJournalpostClient = clients.queryJournalpostClient,
            stansAvYtelseService = stansAvYtelseService,
            personService = kjerneTjenester.personService,
        )
    }

    private fun buildRevurderingService(
        databaseRepos: DatabaseRepos,
        kjerneTjenester: KjerneTjenester,
        vedtakService: VedtakServiceImpl,
        mottakerService: MottakerServiceImpl,
        kontrollsamtaleSetup: KontrollsamtaleSetup,
        formuegrenserFactory: FormuegrenserFactory,
        satsFactory: SatsFactory,
        clock: Clock,
    ): RevurderingServiceImpl {
        return RevurderingServiceImpl(
            utbetalingService = kjerneTjenester.utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            oppgaveService = kjerneTjenester.oppgaveService,
            personService = kjerneTjenester.personService,
            brevService = kjerneTjenester.brevService,
            mottakerService = mottakerService,
            clock = clock,
            vedtakService = vedtakService,
            sessionFactory = databaseRepos.sessionFactory,
            formuegrenserFactory = formuegrenserFactory,
            sakService = kjerneTjenester.sakService,
            satsFactory = satsFactory,
            annullerKontrollsamtaleService = kontrollsamtaleSetup.annullerKontrollsamtaleService,
            klageRepo = databaseRepos.klageRepo,
            fritekstService = kjerneTjenester.fritekstService,
            sakStatistikkService = kjerneTjenester.sakStatistikkService,
        ).apply { addObserver(kjerneTjenester.statistikkEventObserver) }
    }

    private fun buildGjenopptaYtelseService(
        databaseRepos: DatabaseRepos,
        kjerneTjenester: KjerneTjenester,
        vedtakService: VedtakServiceImpl,
        clock: Clock,
    ): GjenopptaYtelseServiceImpl {
        return GjenopptaYtelseServiceImpl(
            utbetalingService = kjerneTjenester.utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            clock = clock,
            vedtakService = vedtakService,
            sakService = kjerneTjenester.sakService,
            sessionFactory = databaseRepos.sessionFactory,
            sakStatistikkService = kjerneTjenester.sakStatistikkService,
        ).apply { addObserver(kjerneTjenester.statistikkEventObserver) }
    }

    private fun buildReguleringServices(
        databaseRepos: DatabaseRepos,
        kjerneTjenester: KjerneTjenester,
        vedtakService: VedtakServiceImpl,
        satsFactory: SatsFactory,
        clients: Clients,
        clock: Clock,
    ): ReguleringServices {
        val reguleringService = ReguleringServiceImpl(
            reguleringRepo = databaseRepos.reguleringRepo,
            utbetalingService = kjerneTjenester.utbetalingService,
            vedtakService = vedtakService,
            sessionFactory = databaseRepos.sessionFactory,
            satsFactory = satsFactory,
            clock = clock,
        )
        val reguleringManuellService = ReguleringManuellServiceImpl(
            reguleringRepo = databaseRepos.reguleringRepo,
            sakService = kjerneTjenester.sakService,
            reguleringService = reguleringService,
            clock = clock,
            statistikkService = kjerneTjenester.sakStatistikkService,
            sessionFactory = databaseRepos.sessionFactory,
        )
        val reguleringHentEksterneReguleringerService = ReguleringHentEksterneReguleringerService(
            pesysClient = clients.pesysklient,
            clock = clock,
        )
        val reguleringAutomatiskService = ReguleringAutomatiskServiceImpl(
            reguleringRepo = databaseRepos.reguleringRepo,
            sakService = kjerneTjenester.sakService,
            satsFactory = satsFactory,
            reguleringService = reguleringService,
            clock = clock,
            statistikkService = kjerneTjenester.sakStatistikkService,
            sessionFactory = databaseRepos.sessionFactory,
            reguleringHentEksterneReguleringerService = reguleringHentEksterneReguleringerService,
        )
        return ReguleringServices(
            reguleringManuellService = reguleringManuellService,
            reguleringAutomatiskService = reguleringAutomatiskService,
        )
    }

    private fun buildKlageServices(
        databaseRepos: DatabaseRepos,
        clients: Clients,
        kjerneTjenester: KjerneTjenester,
        vedtakService: VedtakServiceImpl,
        clock: Clock,
        mottakerService: MottakerServiceImpl,
    ): KlageServices {
        val klageService = KlageServiceImpl(
            sakService = kjerneTjenester.sakService,
            klageRepo = databaseRepos.klageRepo,
            vedtakService = vedtakService,
            brevService = kjerneTjenester.brevService,
            klageClient = clients.klageClient,
            sessionFactory = databaseRepos.sessionFactory,
            oppgaveService = kjerneTjenester.oppgaveService,
            mottakerService = mottakerService,
            queryJournalpostClient = clients.queryJournalpostClient,
            clock = clock,
            dokumentHendelseRepo = databaseRepos.dokumentHendelseRepo,
            sakStatistikkService = kjerneTjenester.sakStatistikkService,
        ).apply { addObserver(kjerneTjenester.statistikkEventObserver) }
        val klageinstanshendelseService = KlageinstanshendelseServiceImpl(
            klageinstanshendelseRepo = databaseRepos.klageinstanshendelseRepo,
            klageRepo = databaseRepos.klageRepo,
            oppgaveService = kjerneTjenester.oppgaveService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
        )
        return KlageServices(
            klageService = klageService,
            klageinstanshendelseService = klageinstanshendelseService,
        )
    }

    private fun buildIverksettSøknadsbehandlingService(
        databaseRepos: DatabaseRepos,
        kjerneTjenester: KjerneTjenester,
        vedtakService: VedtakServiceImpl,
        kontrollsamtaleSetup: KontrollsamtaleSetup,
        ferdigstillVedtakService: FerdigstillVedtakServiceImpl,
        skattDokumentService: SkattDokumentService,
        satsFactory: SatsFactory,
        clock: Clock,
        mottakerService: MottakerServiceImpl,
    ): IverksettSøknadsbehandlingServiceImpl {
        return IverksettSøknadsbehandlingServiceImpl(
            sakService = kjerneTjenester.sakService,
            clock = clock,
            utbetalingService = kjerneTjenester.utbetalingService,
            sessionFactory = databaseRepos.sessionFactory,
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            vedtakService = vedtakService,
            opprettPlanlagtKontrollsamtaleService = kontrollsamtaleSetup.opprettPlanlagtKontrollsamtaleService,
            ferdigstillVedtakService = ferdigstillVedtakService,
            brevService = kjerneTjenester.brevService,
            skattDokumentService = skattDokumentService,
            satsFactory = satsFactory,
            fritekstService = kjerneTjenester.fritekstService,
            sakStatistikkService = kjerneTjenester.sakStatistikkService,
            mottakerService = mottakerService,
        ).apply {
            addObserver(kjerneTjenester.statistikkEventObserver)
        }
    }

    private fun buildLukkSøknadService(
        databaseRepos: DatabaseRepos,
        kjerneTjenester: KjerneTjenester,
        søknadService: SøknadServiceImpl,
        søknadsbehandlingService: SøknadsbehandlingServiceImpl,
        clock: Clock,
    ): LukkSøknadServiceImpl {
        return LukkSøknadServiceImpl(
            clock = clock,
            søknadService = søknadService,
            brevService = kjerneTjenester.brevService,
            oppgaveService = kjerneTjenester.oppgaveService,
            søknadsbehandlingService = søknadsbehandlingService,
            sakService = kjerneTjenester.sakService,
            sessionFactory = databaseRepos.sessionFactory,
            sakStatistikkService = kjerneTjenester.sakStatistikkService,
        ).apply {
            addObserver(kjerneTjenester.statistikkEventObserver)
        }
    }
}
