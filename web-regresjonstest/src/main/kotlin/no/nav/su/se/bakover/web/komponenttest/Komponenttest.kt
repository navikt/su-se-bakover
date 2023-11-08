package no.nav.su.se.bakover.web.komponenttest

import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.dokument.application.DokumentServices
import no.nav.su.se.bakover.dokument.application.consumer.JournalførDokumentHendelserKonsument
import no.nav.su.se.bakover.dokument.infrastructure.DokumentRepos
import no.nav.su.se.bakover.dokument.infrastructure.Dokumentkomponenter
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.web.Consumers
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.ServiceBuilder
import no.nav.su.se.bakover.web.services.Services
import no.nav.su.se.bakover.web.susebakover
import org.mockito.kotlin.mock
import tilbakekreving.application.service.TilbakekrevingServices
import tilbakekreving.application.service.Tilbakekrevingskomponenter
import tilbakekreving.infrastructure.repo.TilbakekrevingRepos
import tilbakekreving.presentation.consumer.KravgrunnlagDtoMapper
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringConsumer
import java.time.Clock
import java.time.LocalDate
import javax.sql.DataSource

class AppComponents private constructor(
    val clock: Clock,
    val applicationConfig: ApplicationConfig,
    val databaseRepos: DatabaseRepos,
    val clients: Clients,
    val services: Services,
    val tilbakekrevingskomponenter: Tilbakekrevingskomponenter,
    val dokumentHendelseKomponenter: Dokumentkomponenter,
    val accessCheckProxy: AccessCheckProxy,
    val consumers: Consumers,
) {
    companion object {
        fun instance(
            clock: Clock,
            applicationConfig: ApplicationConfig,
            dataSource: DataSource,
            repoBuilder: (dataSource: DataSource, clock: Clock, satsFactory: SatsFactoryForSupplerendeStønad) -> DatabaseRepos,
            clientBuilder: (databaseRepos: DatabaseRepos, clock: Clock) -> Clients,
            serviceBuilder: (databaseRepos: DatabaseRepos, clients: Clients, clock: Clock, satsFactory: SatsFactoryForSupplerendeStønad) -> Services,
            tilbakekrevingskomponenterBuilder: (databaseRepos: DatabaseRepos, services: Services) -> Tilbakekrevingskomponenter,
            dokumentKomponenterBuilder: (databaseRepos: DatabaseRepos, services: Services, clients: Clients) -> Dokumentkomponenter,
        ): AppComponents {
            val databaseRepos = repoBuilder(dataSource, clock, satsFactoryTest)
            val clients = clientBuilder(databaseRepos, clock)
            val services: Services = serviceBuilder(databaseRepos, clients, clock, satsFactoryTest)
            val accessCheckProxy = AccessCheckProxy(
                personRepo = databaseRepos.person,
                services = services,
            )
            val consumers = Consumers(
                utbetalingKvitteringConsumer = UtbetalingKvitteringConsumer(
                    utbetalingService = services.utbetaling,
                    ferdigstillVedtakService = services.ferdigstillVedtak,
                    clock = clock,
                ),
            )
            val tilbakekrevingskomponenter = tilbakekrevingskomponenterBuilder(databaseRepos, services)
            val dokumenterKomponenter = dokumentKomponenterBuilder(databaseRepos, services, clients)
            return AppComponents(
                clock = clock,
                applicationConfig = applicationConfig,
                databaseRepos = databaseRepos,
                clients = clients,
                services = services,
                accessCheckProxy = accessCheckProxy,
                consumers = consumers,
                tilbakekrevingskomponenter = tilbakekrevingskomponenter,
                dokumentHendelseKomponenter = dokumenterKomponenter,
            )
        }
    }
}

internal fun withKomptestApplication(
    clock: Clock = fixedClock,
    applicationConfig: ApplicationConfig = applicationConfig(),
    repoBuilder: (dataSource: DataSource, clock: Clock, satsFactory: SatsFactoryForSupplerendeStønad) -> DatabaseRepos = { dataSource, klokke, satsFactory ->
        SharedRegressionTestData.databaseRepos(
            dataSource = dataSource,
            clock = klokke,
            // TODO uheldig at vi ikke kan overstyre denne med satsFactory.gjeldende(LocalDate.now(clock))
            satsFactory = satsFactory,
        )
    },
    clientsBuilder: (databaseRepos: DatabaseRepos, clock: Clock) -> Clients = { databaseRepos, klokke ->
        TestClientsBuilder(
            clock = klokke,
            databaseRepos = databaseRepos,
        ).build(applicationConfig)
    },
    serviceBuilder: (databaseRepos: DatabaseRepos, clients: Clients, clock: Clock, satsFactory: SatsFactoryForSupplerendeStønad) -> Services = { databaseRepos, clients, klokke, satsFactory ->
        ServiceBuilder.build(
            databaseRepos = databaseRepos,
            clients = clients,
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = klokke,
            satsFactory = satsFactory.gjeldende(LocalDate.now(klokke)),
            applicationConfig = applicationConfig,
            dbMetrics = dbMetricsStub,
        )
    },
    tilbakekrevingskomponenterBuilder: (databaseRepos: DatabaseRepos, services: Services) -> Tilbakekrevingskomponenter = { databaseRepos, services ->
        val repos = TilbakekrevingRepos(
            clock = clock,
            sessionFactory = databaseRepos.sessionFactory,
            hendelseRepo = databaseRepos.hendelseRepo,
            hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
            oppgaveHendelseRepo = databaseRepos.oppgaveHendelseRepo,
        )
        Tilbakekrevingskomponenter(
            repos = repos,
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
                mapRåttKravgrunnlag = KravgrunnlagDtoMapper::toKravgrunnlag,
                oppgaveService = services.oppgave,
                oppgaveHendelseRepo = repos.oppgaveHendelseRepo,
                hendelseRepo = repos.hendelseRepo,
                brevService = services.brev,
                dokumentHendelseRepo = repos.dokumentHendelseRepo,
            ),
        )
    },
    dokumentKomponenterBuilder: (databaseRepos: DatabaseRepos, services: Services, clients: Clients) -> Dokumentkomponenter = { databaseRepos, services, clients ->
        val repos = DokumentRepos(
            clock = clock,
            sessionFactory = databaseRepos.sessionFactory,
            hendelseRepo = databaseRepos.hendelseRepo,
            hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
            dokumentHendelseRepo = databaseRepos.dokumentHendelseRepo,
        )
        Dokumentkomponenter(
            repos = repos,
            services = DokumentServices(
                clock = clock,
                sessionFactory = repos.sessionFactory,
                personService = services.person,
                hendelsekonsumenterRepo = repos.hendelsekonsumenterRepo,
                sakService = services.sak,
                hendelseRepo = repos.hendelseRepo,
                dokumentHendelseRepo = repos.dokumentHendelseRepo,
                dokArkiv = clients.dokArkiv,
                journalførtDokumentHendelserKonsument = JournalførDokumentHendelserKonsument(
                    sakService = services.sak,
                    personService = services.person,
                    dokArkiv = clients.dokArkiv,
                    dokumentHendelseRepo = repos.dokumentHendelseRepo,
                    hendelsekonsumenterRepo = repos.hendelsekonsumenterRepo,
                    hendelseRepo = repos.hendelseRepo,
                    sessionFactory = repos.sessionFactory,
                    clock = clock,
                ),
            ),

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
                applicationConfig = applicationConfig,
                tilbakekrevingskomponenterBuilder = tilbakekrevingskomponenterBuilder,
                dokumentKomponenterBuilder = dokumentKomponenterBuilder,
            ),
            test = test,
        )
    }
}

fun Application.testSusebakover(appComponents: AppComponents) {
    return susebakover(
        clock = appComponents.clock,
        applicationConfig = appComponents.applicationConfig,
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
