package no.nav.su.se.bakover.web.komponenttest

import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.finn.unleash.FakeUnleash
import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.web.Consumers
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingConsumer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringConsumer
import no.nav.su.se.bakover.web.susebakover
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.LocalDate
import javax.sql.DataSource

class AppComponents private constructor(
    val clock: Clock,
    val databaseRepos: DatabaseRepos,
    val clients: Clients,
    val unleash: Unleash,
    val services: Services,
    val accessCheckProxy: AccessCheckProxy,
    val consumers: Consumers,
) {
    companion object {
        fun instance(
            clock: Clock,
            dataSource: DataSource,
            repoBuilder: (dataSource: DataSource, clock: Clock, satsFactory: SatsFactoryForSupplerendeStønad) -> DatabaseRepos,
            clientBuilder: (databaseRepos: DatabaseRepos, clock: Clock) -> Clients,
            serviceBuilder: (databaseRepos: DatabaseRepos, clients: Clients, clock: Clock, satsFactory: SatsFactoryForSupplerendeStønad, unleash: Unleash) -> Services,
        ): AppComponents {
            val databaseRepos = repoBuilder(dataSource, clock, satsFactoryTest)
            val clients = clientBuilder(databaseRepos, clock)
            val unleash = FakeUnleash().apply { enableAll() }
            val services: Services = serviceBuilder(databaseRepos, clients, clock, satsFactoryTest, unleash)
            val accessCheckProxy = AccessCheckProxy(
                personRepo = databaseRepos.person,
                services = services,
            )
            val consumers = Consumers(
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
            )
            return AppComponents(
                clock = clock,
                databaseRepos = databaseRepos,
                clients = clients,
                unleash = unleash,
                services = services,
                accessCheckProxy = accessCheckProxy,
                consumers = consumers,
            )
        }
    }
}

internal fun withKomptestApplication(
    clock: Clock = fixedClock,
    repoBuilder: (dataSource: DataSource, clock: Clock, satsFactory: SatsFactoryForSupplerendeStønad) -> DatabaseRepos = { dataSource, klokke, satsFactory ->
        SharedRegressionTestData.databaseRepos(
            dataSource = dataSource,
            clock = klokke,
            satsFactory = satsFactory, // TODO uheldig at vi ikke kan overstyre denne med satsFactory.gjeldende(LocalDate.now(clock))
        )
    },
    clientsBuilder: (databaseRepos: DatabaseRepos, clock: Clock) -> Clients = { databaseRepos, klokke ->
        TestClientsBuilder(
            clock = klokke,
            databaseRepos = databaseRepos,
        ).build(applicationConfig())
    },
    serviceBuilder: (databaseRepos: DatabaseRepos, clients: Clients, clock: Clock, satsFactory: SatsFactoryForSupplerendeStønad, unleash: Unleash) -> Services = { databaseRepos, clients, klokke, satsFactory, unleash ->
        ServiceBuilder.build(
            databaseRepos = databaseRepos,
            clients = clients,
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = klokke,
            unleash = unleash,
            satsFactory = satsFactory.gjeldende(LocalDate.now(klokke)),
            applicationConfig = applicationConfig(),
        )
    },
    test: ApplicationTestBuilder.(appComponents: AppComponents) -> Unit,
) {
    withMigratedDb { dataSource ->
        testApplication(
            appComponents = AppComponents.instance(
                clock = clock,
                dataSource = dataSource,
                repoBuilder = repoBuilder,
                clientBuilder = clientsBuilder,
                serviceBuilder = serviceBuilder,
            ),
            test = test,
        )
    }
}

private fun Application.testSusebakover(appComponents: AppComponents) {
    return susebakover(
        clock = appComponents.clock,
        applicationConfig = applicationConfig(),
        unleash = appComponents.unleash,
        databaseRepos = appComponents.databaseRepos,
        clients = appComponents.clients,
        services = appComponents.services,
        accessCheckProxy = appComponents.accessCheckProxy,
    )
}

fun testApplication(
    appComponents: AppComponents,
    test: ApplicationTestBuilder.(appComponents: AppComponents) -> Unit,
) {
    testApplication {
        application {
            testSusebakover(appComponents)
        }
        test(appComponents)
    }
}
