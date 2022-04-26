package no.nav.su.se.bakover.web.komponenttest

import io.ktor.server.application.Application
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.withApplication
import no.finn.unleash.FakeUnleash
import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.susebakover
import org.mockito.kotlin.mock
import java.time.Clock
import javax.sql.DataSource

class AppComponents private constructor(
    val clock: Clock,
    val databaseRepos: DatabaseRepos,
    val clients: Clients,
    val unleash: Unleash,
    val services: Services,
    val accessCheckProxy: AccessCheckProxy,
) {
    companion object {
        fun instance(clock: Clock, dataSource: DataSource): AppComponents {
            val databaseRepos: DatabaseRepos = SharedRegressionTestData.databaseRepos(
                dataSource = dataSource,
                clock = clock,
            )
            val clients: Clients = TestClientsBuilder(
                clock = clock,
                databaseRepos = databaseRepos,
            ).build(SharedRegressionTestData.applicationConfig)
            val unleash: Unleash = FakeUnleash().apply { enableAll() }
            val services: Services = ServiceBuilder.build(
                databaseRepos = databaseRepos,
                clients = clients,
                behandlingMetrics = mock(),
                søknadMetrics = mock(),
                clock = clock,
                unleash = unleash,
            )
            val accessCheckProxy = AccessCheckProxy(
                personRepo = databaseRepos.person,
                services = services,
            )
            return AppComponents(
                clock = clock,
                databaseRepos = SharedRegressionTestData.databaseRepos(dataSource = dataSource, clock = clock),
                clients = clients,
                unleash = unleash,
                services = services,
                accessCheckProxy = accessCheckProxy,
            )
        }
    }
}

internal fun withKomptestApplication(
    clock: Clock = fixedClock,
    test: TestApplicationEngine.(appComponents: AppComponents) -> Unit,
) {
    withMigratedDb { dataSource ->
        val appComponents = AppComponents.instance(clock, dataSource)
        testApplication(
            appComponents = appComponents,
            moduleFunction = {
                testSusebakover(appComponents)
            },
            test = test,
        )
    }
}

private fun Application.testSusebakover(appComponents: AppComponents) {
    return susebakover(
        clock = appComponents.clock,
        databaseRepos = appComponents.databaseRepos,
        clients = appComponents.clients,
        services = appComponents.services,
        unleash = appComponents.unleash,
        accessCheckProxy = appComponents.accessCheckProxy,
        applicationConfig = SharedRegressionTestData.applicationConfig,
    )
}

fun <R> testApplication(
    appComponents: AppComponents,
    moduleFunction: Application.(appComponents: AppComponents) -> Unit,
    test: TestApplicationEngine.(appComponents: AppComponents) -> R,
): R {
    return withApplication(createTestEnvironment()) {
        moduleFunction(application, appComponents)
        test(appComponents)
    }
}
