package no.nav.su.se.bakover.web

import io.getunleash.Unleash
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.Route
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ProdClientsBuilder
import no.nav.su.se.bakover.client.StubClientsBuilder
import no.nav.su.se.bakover.client.UnleashBuilder
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DomainToQueryParameterMapper
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.metrics.ClientMetrics
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.web.metrics.BehandlingMicrometerMetrics
import no.nav.su.se.bakover.web.metrics.DbMicrometerMetrics
import no.nav.su.se.bakover.web.metrics.JournalpostClientMicrometerMetrics
import no.nav.su.se.bakover.web.metrics.SøknadMicrometerMetrics
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.ServiceBuilder
import no.nav.su.se.bakover.web.services.Services
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingConsumer
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringConsumer
import java.time.Clock
import java.time.LocalDate

/**
 * Kun ment til brukt fra produksjons-main. Testene og lokal bør starte sin egen embeddedServer.
 */
fun startServer() {
    embeddedServer(factory = Netty, port = 8080, module = {
        susebakover()
    }).start(true)
}

fun Application.susebakover(
    clock: Clock = Clock.systemUTC(),
    behandlingMetrics: BehandlingMetrics = BehandlingMicrometerMetrics(),
    søknadMetrics: SøknadMetrics = SøknadMicrometerMetrics(),
    clientMetrics: ClientMetrics = ClientMetrics(
        journalpostClientMetrics = JournalpostClientMicrometerMetrics(),
    ),
    dbMetrics: DbMetrics = DbMicrometerMetrics(),
    applicationConfig: ApplicationConfig = ApplicationConfig.createConfig(),
    unleash: Unleash = UnleashBuilder.build(applicationConfig),
    satsFactory: SatsFactoryForSupplerendeStønad = SatsFactoryForSupplerendeStønad(),
    databaseRepos: DatabaseRepos = DatabaseBuilder.build(
        databaseConfig = applicationConfig.database,
        dbMetrics = dbMetrics,
        clock = clock,
        satsFactory = satsFactory,
        queryParameterMappers = listOf(DomainToQueryParameterMapper),
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
            unleash = unleash,
            metrics = clientMetrics,
        ).build(applicationConfig)
    },
    services: Services = ServiceBuilder.build(
        databaseRepos = databaseRepos,
        clients = clients,
        behandlingMetrics = behandlingMetrics,
        søknadMetrics = søknadMetrics,
        clock = clock,
        unleash = unleash,
        satsFactory = satsFactory.gjeldende(LocalDate.now(clock)),
        applicationConfig = applicationConfig,
        dbMetrics = dbMetrics,
    ),
    accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
    consumers: Consumers = Consumers(
        tilbakekrevingConsumer = TilbakekrevingConsumer(
            tilbakekrevingService = services.tilbakekrevingService,
            revurderingService = services.revurdering,
            clock = clock,
        ),
        utbetalingKvitteringConsumer = UtbetalingKvitteringConsumer(
            utbetalingService = services.utbetaling,
            ferdigstillVedtakService = services.ferdigstillVedtak,
            clock = clock,
        ),
    ),
    extraRoutes: Route.(services: Services) -> Unit = {},
) {
    val satsFactoryIDag = satsFactory.gjeldende(LocalDate.now(clock))

    setupKtor(
        services = services,
        clock = clock,
        applicationConfig = applicationConfig,
        accessCheckProxy = accessCheckProxy,
        clients = clients,
        satsFactoryIDag = satsFactoryIDag,
        databaseRepos = databaseRepos,
        extraRoutes = extraRoutes,
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
    )
}
