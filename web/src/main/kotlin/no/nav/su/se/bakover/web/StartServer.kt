package no.nav.su.se.bakover.web

import beregning.domain.BeregningStrategyFactory
import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.Route
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ProdClientsBuilder
import no.nav.su.se.bakover.client.StubClientsBuilder
import no.nav.su.se.bakover.client.sts.StsSamlClient
import no.nav.su.se.bakover.common.domain.auth.SamlTokenProvider
import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DomainToQueryParameterMapper
import no.nav.su.se.bakover.dokument.application.DokumentServices
import no.nav.su.se.bakover.dokument.infrastructure.Dokumentkomponenter
import no.nav.su.se.bakover.dokument.infrastructure.database.DokumentRepos
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.service.dokument.DistribuerDokumentService
import no.nav.su.se.bakover.web.metrics.DbMicrometerMetrics
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.ServiceBuilder
import no.nav.su.se.bakover.web.services.Services
import satser.domain.SatsFactory
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlagTilHendelse
import tilbakekreving.presentation.Tilbakekrevingskomponenter
import tilbakekreving.presentation.consumer.KravgrunnlagDtoMapper
import tilgangstyring.application.TilgangstyringService
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.application.utbetaling.ResendUtbetalingService
import java.time.Clock
import java.time.LocalDate

/**
 * Kun ment til brukt fra produksjons-main. Testene og lokal bør starte sin egen embeddedServer.
 */
fun startServer() {
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = {
            susebakover()
        },
    ).start(true)
}

val mapRåttKravgrunnlag = KravgrunnlagDtoMapper::toKravgrunnlag
val mapRåttKravgrunnlagPåSakHendelse = KravgrunnlagDtoMapper::toKravgrunnlagPåSakHendelse

/**
 * @param disableConsumersAndJobs Kun for testene.
 */
fun Application.susebakover(
    clock: Clock = Clock.systemUTC(),
    suMetrics: SuMetrics = SuMetrics(),
    dbMetrics: DbMetrics = DbMicrometerMetrics(suMetrics),
    applicationConfig: ApplicationConfig = ApplicationConfig.createConfig(),
    satsFactory: SatsFactoryForSupplerendeStønad = SatsFactoryForSupplerendeStønad(),
    satsFactoryIDag: SatsFactory = satsFactory.gjeldende(LocalDate.now(clock)),
    formuegrenserFactoryIDag: FormuegrenserFactory = FormuegrenserFactory.createFromGrunnbeløp(
        grunnbeløpFactory = satsFactoryIDag.grunnbeløpFactory,
        tidligsteTilgjengeligeMåned = satsFactoryIDag.tidligsteTilgjengeligeMåned,
    ),
    databaseRepos: DatabaseRepos = DatabaseBuilder.build(
        databaseConfig = applicationConfig.database,
        dbMetrics = dbMetrics,
        clock = clock,
        satsFactory = satsFactory,
        queryParameterMappers = listOf(DomainToQueryParameterMapper),
        råttKravgrunnlagMapper = mapRåttKravgrunnlag,
    ),
    jmsConfig: JmsConfig = JmsConfig(applicationConfig),
    samlTokenProvider: SamlTokenProvider = StsSamlClient(
        baseUrl = applicationConfig.clientsConfig.stsSamlUrl,
        serviceUser = applicationConfig.serviceUser,
        clock = clock,
    ),
    clients: Clients = if (applicationConfig.runtimeEnvironment != ApplicationConfig.RuntimeEnvironment.Nais) {
        StubClientsBuilder(
            clock = clock,
            databaseRepos = databaseRepos,
        ).build(applicationConfig)
    } else {
        ProdClientsBuilder(
            jmsConfig,
            clock = clock,
            samlTokenProvider = samlTokenProvider,
            suMetrics = suMetrics,
        ).build(applicationConfig)
    },
    services: Services = run {
        ServiceBuilder.build(
            databaseRepos = databaseRepos,
            clients = clients,
            clock = clock,
            satsFactory = satsFactoryIDag,
            formuegrenserFactory = formuegrenserFactoryIDag,
            applicationConfig = applicationConfig,
            dbMetrics = dbMetrics,
        )
    },
    tilbakekrevingskomponenter: (
        clock: Clock,
        sessionFactory: SessionFactory,
        hendelsekonsumenterRepo: HendelsekonsumenterRepo,
        sakService: SakService,
        oppgaveService: OppgaveService,
        oppgaveHendelseRepo: OppgaveHendelseRepo,
        mapRåttKravgrunnlag: MapRåttKravgrunnlagTilHendelse,
        hendelseRepo: HendelseRepo,
        dokumentHendelseRepo: DokumentHendelseRepo,
        brevService: BrevService,
        tilbakekrevingConfig: TilbakekrevingConfig,
        tilgangstyringService: TilgangstyringService,
    ) -> Tilbakekrevingskomponenter = { clockFunParam, sessionFactory, hendelsekonsumenterRepo, sak, oppgave, oppgaveHendelseRepo, mapRåttKravgrunnlagPåSakHendelse, hendelseRepo, dokumentHendelseRepo, brevService, tilbakekrevingConfig, _tilgangstyringService ->
        Tilbakekrevingskomponenter.create(
            clock = clockFunParam,
            sessionFactory = sessionFactory,
            hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            sakService = sak,
            oppgaveService = oppgave,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            mapRåttKravgrunnlagPåSakHendelse = mapRåttKravgrunnlagPåSakHendelse,
            hendelseRepo = hendelseRepo,
            dokumentHendelseRepo = dokumentHendelseRepo,
            brevService = brevService,
            tilbakekrevingConfig = tilbakekrevingConfig,
            dbMetrics = dbMetrics,
            samlTokenProvider = samlTokenProvider,
            tilgangstyringService = _tilgangstyringService,
        )
    },
    dokumentkomponenter: Dokumentkomponenter = run {
        val dokumentRepos = DokumentRepos(
            clock = clock,
            sessionFactory = databaseRepos.sessionFactory,
            hendelseRepo = databaseRepos.hendelseRepo,
            hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
        )
        Dokumentkomponenter(
            repos = dokumentRepos,
            services = DokumentServices(
                clock = clock,
                sessionFactory = databaseRepos.sessionFactory,
                hendelsekonsumenterRepo = dokumentRepos.hendelsekonsumenterRepo,
                sakService = services.sak,
                dokumentHendelseRepo = dokumentRepos.dokumentHendelseRepo,
                journalførBrevClient = clients.journalførClients.brev,
                dokDistFordeling = clients.dokDistFordeling,
            ),
        )
    },
    accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
    beregningStrategyFactory: BeregningStrategyFactory = BeregningStrategyFactory(
        clock = clock,
        satsFactory = satsFactoryIDag,
    ),
    resendUtbetalingService: ResendUtbetalingService = ResendUtbetalingService(
        utbetalingService = services.utbetaling,
        vedtakRepo = databaseRepos.vedtakRepo,
        sakService = services.sak,
        sessionFactory = databaseRepos.sessionFactory,
        clock = clock,
        serviceUser = applicationConfig.serviceUser.username,
        beregningStrategyFactory = beregningStrategyFactory,
    ),
    disableConsumersAndJobs: Boolean = false,
    tilgangstyringService: TilgangstyringService = TilgangstyringService(
        personService = services.person,
    ),
    distribuerDokumentService: DistribuerDokumentService = DistribuerDokumentService(
        dokDistFordeling = clients.dokDistFordeling,
        dokumentRepo = databaseRepos.dokumentRepo,
        dokumentHendelseRepo = dokumentkomponenter.repos.dokumentHendelseRepo,
        distribuerDokumentHendelserKonsument = dokumentkomponenter.services.distribuerDokumentHendelserKonsument,
        tilgangstyringService = tilgangstyringService,
        clock = clock,
    ),
    extraRoutes: Route.(services: Services) -> Unit = {},
) {
    tilbakekrevingskomponenter(
        clock,
        databaseRepos.sessionFactory,
        databaseRepos.hendelsekonsumenterRepo,
        services.sak,
        services.oppgave,
        databaseRepos.oppgaveHendelseRepo,
        mapRåttKravgrunnlagPåSakHendelse,
        databaseRepos.hendelseRepo,
        databaseRepos.dokumentHendelseRepo,
        services.brev,
        applicationConfig.oppdrag.tilbakekreving,
        tilgangstyringService,
    ).also {
        setupKtor(
            services = services,
            clock = clock,
            applicationConfig = applicationConfig,
            accessCheckProxy = accessCheckProxy,
            clients = clients,
            formuegrenserFactoryIDag = formuegrenserFactoryIDag,
            databaseRepos = databaseRepos,
            extraRoutes = extraRoutes,
            tilbakekrevingskomponenter = it,
            resendUtbetalingService = resendUtbetalingService,
            suMetrics = suMetrics,
            distribuerDokumentService = distribuerDokumentService,
        )
        if (!disableConsumersAndJobs) {
            val jobberOgConsumers = startJobberOgConsumers(
                services = services,
                clients = clients,
                databaseRepos = databaseRepos,
                applicationConfig = applicationConfig,
                jmsConfig = jmsConfig,
                clock = clock,
                dbMetrics = dbMetrics,
                tilbakekrevingskomponenter = it,
                dokumentKomponenter = dokumentkomponenter,
                distribuerDokumentService = distribuerDokumentService,
            )
            this.monitor.subscribe(ApplicationStopping) {
                jobberOgConsumers.stop()
                databaseRepos.sessionFactory.close()
            }
        }
    }
}
