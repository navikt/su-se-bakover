package no.nav.su.se.bakover.web.komponenttest

import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.finn.unleash.FakeUnleash
import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.sak.SaksnummerFactoryProd
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
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
            clientBuilder: (databaseRepos: DatabaseRepos) -> Clients,
        ): AppComponents {
            val satsFactory = satsFactoryTestPåDato(LocalDate.now(clock))
            val databaseRepos: DatabaseRepos = SharedRegressionTestData.databaseRepos(
                dataSource = dataSource,
                clock = clock,
                satsFactory = satsFactoryTest,
            )
            val clients = clientBuilder(databaseRepos)
            val unleash: Unleash = FakeUnleash().apply { enableAll() }
            val services: Services = ServiceBuilder.build(
                databaseRepos = databaseRepos,
                clients = clients,
                behandlingMetrics = mock(),
                søknadMetrics = mock(),
                clock = clock,
                unleash = unleash,
                satsFactory = satsFactory,
                saksnummerFactory = SaksnummerFactoryProd(databaseRepos.sak::hentNesteSaksnummer)
            )
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
    clients: (databaseRepos: DatabaseRepos) -> Clients = {
        TestClientsBuilder(
            clock = clock,
            databaseRepos = it,
        ).build(SharedRegressionTestData.applicationConfig)
    },
    test: ApplicationTestBuilder.(appComponents: AppComponents) -> Unit,
) {
    withMigratedDb { dataSource ->
        val appComponents = AppComponents.instance(clock, dataSource, clients)
        testApplication(
            appComponents = appComponents,
            test = test,
        )
    }
}

private fun Application.testSusebakover(appComponents: AppComponents) {
    return susebakover(
        clock = appComponents.clock,
        applicationConfig = SharedRegressionTestData.applicationConfig,
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
