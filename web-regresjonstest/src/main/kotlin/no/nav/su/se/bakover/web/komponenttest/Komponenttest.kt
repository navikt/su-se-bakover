package no.nav.su.se.bakover.web.komponenttest

import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.dokument.application.DokumentServices
import no.nav.su.se.bakover.dokument.application.consumer.DistribuerDokumentHendelserKonsument
import no.nav.su.se.bakover.dokument.application.consumer.JournalførDokumentHendelserKonsument
import no.nav.su.se.bakover.dokument.infrastructure.database.Dokumentkomponenter
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.auth.FakeSamlTokenProvider
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.web.Consumers
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.mapRåttKravgrunnlagPåSakHendelse
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.ServiceBuilder
import no.nav.su.se.bakover.web.services.Services
import no.nav.su.se.bakover.web.susebakover
import org.mockito.kotlin.mock
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import tilbakekreving.presentation.Tilbakekrevingskomponenter
import vilkår.formue.domain.FormuegrenserFactory
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
        run {
            val satsFactoryIDag = satsFactory.gjeldende(LocalDate.now(klokke))
            val formuegrenserFactoryIDag = FormuegrenserFactory.createFromGrunnbeløp(
                grunnbeløpFactory = satsFactoryIDag.grunnbeløpFactory,
                tidligsteTilgjengeligeMåned = satsFactoryIDag.tidligsteTilgjengeligeMåned,
            )
            ServiceBuilder.build(
                databaseRepos = databaseRepos,
                clients = clients,
                behandlingMetrics = mock(),
                søknadMetrics = mock(),
                clock = klokke,
                satsFactory = satsFactoryIDag,
                formuegrenserFactory = formuegrenserFactoryIDag,
                applicationConfig = applicationConfig,
                dbMetrics = dbMetricsStub,
            )
        }
    },
    tilbakekrevingskomponenterBuilder: (databaseRepos: DatabaseRepos, services: Services) -> Tilbakekrevingskomponenter = { databaseRepos, services ->
        Tilbakekrevingskomponenter.create(
            clock = clock,
            sessionFactory = databaseRepos.sessionFactory,
            personService = services.person,
            hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
            sakService = services.sak,
            oppgaveService = services.oppgave,
            oppgaveHendelseRepo = databaseRepos.oppgaveHendelseRepo,
            mapRåttKravgrunnlagPåSakHendelse = mapRåttKravgrunnlagPåSakHendelse,
            hendelseRepo = databaseRepos.hendelseRepo,
            dokumentHendelseRepo = databaseRepos.dokumentHendelseRepo,
            brevService = services.brev,
            tilbakekrevingConfig = applicationConfig.oppdrag.tilbakekreving,
            dbMetrics = dbMetricsStub,
            samlTokenProvider = FakeSamlTokenProvider(),
        )
    },
    dokumentKomponenterBuilder: (databaseRepos: DatabaseRepos, services: Services, clients: Clients) -> Dokumentkomponenter = { databaseRepos, services, clients ->
        val repos = no.nav.su.se.bakover.dokument.infrastructure.database.DokumentRepos(
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
                hendelsekonsumenterRepo = repos.hendelsekonsumenterRepo,
                sakService = services.sak,
                dokumentHendelseRepo = repos.dokumentHendelseRepo,
                journalførBrevClient = clients.journalførClients.brev,
                dokDistFordeling = clients.dokDistFordeling,
                journalførtDokumentHendelserKonsument = JournalførDokumentHendelserKonsument(
                    sakService = services.sak,
                    journalførBrevClient = clients.journalførClients.brev,
                    dokumentHendelseRepo = repos.dokumentHendelseRepo,
                    hendelsekonsumenterRepo = repos.hendelsekonsumenterRepo,
                    sessionFactory = repos.sessionFactory,
                    clock = clock,
                ),
                distribuerDokumentHendelserKonsument = DistribuerDokumentHendelserKonsument(
                    sakService = services.sak,
                    dokDistFordeling = clients.dokDistFordeling,
                    hendelsekonsumenterRepo = repos.hendelsekonsumenterRepo,
                    dokumentHendelseRepo = repos.dokumentHendelseRepo,
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
        tilbakekrevingskomponenter = { _, _, _, _, _, _, _, _, _, _, _, _ ->
            appComponents.tilbakekrevingskomponenter
        },
        dokumentkomponenter = appComponents.dokumentHendelseKomponenter,
        consumers = appComponents.consumers,
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
