package no.nav.su.se.bakover.web

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.Route
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ProdClientsBuilder
import no.nav.su.se.bakover.client.StubClientsBuilder
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DomainToQueryParameterMapper
import no.nav.su.se.bakover.dokument.application.DokumentServices
import no.nav.su.se.bakover.dokument.infrastructure.DokumentRepos
import no.nav.su.se.bakover.dokument.infrastructure.Dokumentkomponenter
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.metrics.ClientMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.web.metrics.BehandlingMicrometerMetrics
import no.nav.su.se.bakover.web.metrics.DbMicrometerMetrics
import no.nav.su.se.bakover.web.metrics.JournalpostClientMicrometerMetrics
import no.nav.su.se.bakover.web.metrics.SøknadMicrometerMetrics
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.ServiceBuilder
import no.nav.su.se.bakover.web.services.Services
import sats.domain.SatsFactory
import sats.domain.SatsFactoryForSupplerendeStønad
import tilbakekreving.application.service.TilbakekrevingServices
import tilbakekreving.application.service.Tilbakekrevingskomponenter
import tilbakekreving.infrastructure.repo.TilbakekrevingRepos
import tilbakekreving.presentation.consumer.KravgrunnlagDtoMapper
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringConsumer
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
fun Application.susebakover(
    clock: Clock = Clock.systemUTC(),
    behandlingMetrics: BehandlingMetrics = BehandlingMicrometerMetrics(),
    søknadMetrics: SøknadMetrics = SøknadMicrometerMetrics(),
    clientMetrics: ClientMetrics = ClientMetrics(
        journalpostClientMetrics = JournalpostClientMicrometerMetrics(),
    ),
    dbMetrics: DbMetrics = DbMicrometerMetrics(),
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
    clients: Clients = if (applicationConfig.runtimeEnvironment != ApplicationConfig.RuntimeEnvironment.Nais) {
        StubClientsBuilder(
            clock = clock,
            databaseRepos = databaseRepos,
        ).build(applicationConfig)
    } else {
        ProdClientsBuilder(
            jmsConfig,
            clock = clock,
            metrics = clientMetrics,
        ).build(applicationConfig)
    },
    services: Services = run {
        ServiceBuilder.build(
            databaseRepos = databaseRepos,
            clients = clients,
            behandlingMetrics = behandlingMetrics,
            søknadMetrics = søknadMetrics,
            clock = clock,
            satsFactory = satsFactoryIDag,
            formuegrenserFactory = formuegrenserFactoryIDag,
            applicationConfig = applicationConfig,
            dbMetrics = dbMetrics,
        )
    },
    tilbakekrevingskomponenter: Tilbakekrevingskomponenter = run {
        val repos = TilbakekrevingRepos(
            clock = clock,
            sessionFactory = databaseRepos.sessionFactory,
            hendelseRepo = databaseRepos.hendelseRepo,
            hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
            oppgaveHendelseRepo = databaseRepos.oppgaveHendelseRepo,
        )
        Tilbakekrevingskomponenter(
            services = TilbakekrevingServices(
                clock = clock,
                sessionFactory = databaseRepos.sessionFactory,
                personRepo = databaseRepos.person,
                personService = services.person,
                kravgrunnlagRepo = repos.kravgrunnlagRepo,
                hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
                tilbakekrevingService = services.tilbakekrevingService,
                sakService = services.sak,
                tilbakekrevingsbehandlingRepo = repos.tilbakekrevingsbehandlingRepo,
                oppgaveService = services.oppgave,
                mapRåttKravgrunnlag = mapRåttKravgrunnlagPåSakHendelse,
                oppgaveHendelseRepo = repos.oppgaveHendelseRepo,
                hendelseRepo = repos.hendelseRepo,
                brevService = services.brev,
                dokumentHendelseRepo = repos.dokumentHendelseRepo,
            ),
            repos = repos,
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
            repos = DokumentRepos(
                clock = clock,
                sessionFactory = dokumentRepos.sessionFactory,
                hendelseRepo = dokumentRepos.hendelseRepo,
                hendelsekonsumenterRepo = dokumentRepos.hendelsekonsumenterRepo,
                dokumentHendelseRepo = dokumentRepos.dokumentHendelseRepo,
            ),
            services = DokumentServices(
                clock = clock,
                sessionFactory = databaseRepos.sessionFactory,
                personService = services.person,
                hendelsekonsumenterRepo = dokumentRepos.hendelsekonsumenterRepo,
                sakService = services.sak,
                dokumentHendelseRepo = dokumentRepos.dokumentHendelseRepo,
                journalførBrevClient = clients.journalførClients.brev,
                dokDistFordeling = clients.dokDistFordeling,
            ),
        )
    },
    accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
    consumers: Consumers = Consumers(
        utbetalingKvitteringConsumer = UtbetalingKvitteringConsumer(
            utbetalingService = services.utbetaling,
            ferdigstillVedtakService = services.ferdigstillVedtak,
            clock = clock,
        ),
    ),
    extraRoutes: Route.(services: Services) -> Unit = {},
) {
    setupKtor(
        services = services,
        clock = clock,
        applicationConfig = applicationConfig,
        accessCheckProxy = accessCheckProxy,
        clients = clients,
        formuegrenserFactoryIDag = formuegrenserFactoryIDag,
        databaseRepos = databaseRepos,
        extraRoutes = extraRoutes,
        tilbakekrevingskomponenter = tilbakekrevingskomponenter,
    )
    startJobberOgConsumers(
        services = services,
        clients = clients,
        databaseRepos = databaseRepos,
        applicationConfig = applicationConfig,
        jmsConfig = jmsConfig,
        clock = clock,
        consumers = consumers,
        dbMetrics = dbMetrics,
        tilbakekrevingskomponenter = tilbakekrevingskomponenter,
        dokumentKomponenter = dokumentkomponenter,
    )
}
