package no.nav.su.se.bakover.web.komponenttest

import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.dokument.application.DokumentServices
import no.nav.su.se.bakover.dokument.application.consumer.DistribuerDokumentHendelserKonsument
import no.nav.su.se.bakover.dokument.application.consumer.JournalførDokumentHendelserKonsument
import no.nav.su.se.bakover.dokument.infrastructure.Dokumentkomponenter
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.auth.FakeSamlTokenProvider
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.tilbakekreving.tilbakekrevingskomponenterMedClientStubs
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.mapRåttKravgrunnlagPåSakHendelse
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.ServiceBuilder
import no.nav.su.se.bakover.web.services.Services
import no.nav.su.se.bakover.web.susebakover
import person.domain.PersonOppslag
import person.domain.PersonService
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import tilbakekreving.presentation.Tilbakekrevingskomponenter
import tilgangstyring.application.TilgangstyringService
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.infrastructure.kvittering.UtbetalingskvitteringKomponenter
import økonomi.infrastructure.kvittering.consumer.kvitteringXmlTilSaksnummerOgUtbetalingId
import økonomi.infrastructure.kvittering.xmlMapperForUtbetalingskvittering
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
    val utbetalingskvitteringKomponenter: UtbetalingskvitteringKomponenter,
) {
    companion object {
        fun instance(
            clock: Clock,
            applicationConfig: ApplicationConfig,
            dataSource: DataSource,
            satsFactory: SatsFactoryForSupplerendeStønad,
            repoBuilder: (dataSource: DataSource, clock: Clock, satsFactory: SatsFactoryForSupplerendeStønad) -> DatabaseRepos,
            clientBuilder: (databaseRepos: DatabaseRepos, clock: Clock, applicationConfig: ApplicationConfig) -> Clients,
            serviceBuilder: (databaseRepos: DatabaseRepos, clients: Clients, clock: Clock, satsFactory: SatsFactoryForSupplerendeStønad) -> Services,
            tilbakekrevingskomponenterBuilder: (databaseRepos: DatabaseRepos, services: Services, tilgangstyringService: TilgangstyringService) -> Tilbakekrevingskomponenter,
            dokumentKomponenterBuilder: (databaseRepos: DatabaseRepos, services: Services, clients: Clients) -> Dokumentkomponenter,
            tilgangstyringBuilder: (personService: PersonService) -> TilgangstyringService,
        ): AppComponents {
            val databaseRepos = repoBuilder(dataSource, clock, satsFactory)
            val clients = clientBuilder(databaseRepos, clock, applicationConfig)
            val services: Services = serviceBuilder(databaseRepos, clients, clock, satsFactory)
            val accessCheckProxy = AccessCheckProxy(
                personRepo = databaseRepos.person,
                services = services,
            )
            val tilgangstyringService = tilgangstyringBuilder(services.person)
            val tilbakekrevingskomponenter =
                tilbakekrevingskomponenterBuilder(databaseRepos, services, tilgangstyringService)
            val dokumenterKomponenter = dokumentKomponenterBuilder(databaseRepos, services, clients)
            return AppComponents(
                clock = clock,
                applicationConfig = applicationConfig,
                databaseRepos = databaseRepos,
                clients = clients,
                services = services,
                accessCheckProxy = accessCheckProxy,
                tilbakekrevingskomponenter = tilbakekrevingskomponenter,
                dokumentHendelseKomponenter = dokumenterKomponenter,
                utbetalingskvitteringKomponenter = UtbetalingskvitteringKomponenter.create(
                    sakService = services.sak,
                    sessionFactory = databaseRepos.sessionFactory,
                    clock = clock,
                    hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
                    hendelseRepo = databaseRepos.hendelseRepo as HendelsePostgresRepo,
                    dbMetrics = dbMetricsStub,
                    utbetalingService = services.utbetaling,
                    ferdigstillVedtakService = services.ferdigstillVedtak,
                    xmlMapperForUtbetalingskvittering = kvitteringXmlTilSaksnummerOgUtbetalingId(
                        xmlMapperForUtbetalingskvittering,
                    ),
                ),
            )
        }

        internal fun from(
            dataSource: DataSource,
            clockParam: Clock,
            utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
            satsFactoryParam: SatsFactoryForSupplerendeStønad = satsFactoryTest,
            applicationConfig: ApplicationConfig,
            personOppslagStub: PersonOppslag = PersonOppslagStub(),
        ): AppComponents {
            return instance(
                clock = clockParam,
                dataSource = dataSource,
                satsFactory = satsFactoryParam,
                repoBuilder = { ds, clock, satsFactory ->
                    SharedRegressionTestData.databaseRepos(
                        dataSource = ds,
                        clock = clock,
                        satsFactory = satsFactory,
                    )
                },
                clientBuilder = { db, clock, _applicationConfig ->
                    TestClientsBuilder(
                        clock = clock,
                        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                        databaseRepos = db,
                        personOppslag = personOppslagStub,
                    ).build(_applicationConfig)
                },
                serviceBuilder = { databaseRepos, clients, clock, satsFactory ->
                    run {
                        val satsFactoryIdag = satsFactory.gjeldende(LocalDate.now(clock))
                        val formuegrenserFactoryIDag = FormuegrenserFactory.createFromGrunnbeløp(
                            grunnbeløpFactory = satsFactoryIdag.grunnbeløpFactory,
                            tidligsteTilgjengeligeMåned = satsFactoryIdag.tidligsteTilgjengeligeMåned,
                        )
                        ServiceBuilder.build(
                            databaseRepos = databaseRepos,
                            clients = clients,
                            clock = clock,
                            satsFactory = satsFactoryIdag,
                            formuegrenserFactory = formuegrenserFactoryIDag,
                            applicationConfig = applicationConfig(),
                            dbMetrics = dbMetricsStub,
                        )
                    }
                },
                applicationConfig = applicationConfig,
                tilbakekrevingskomponenterBuilder = { databaseRepos, services, tilgangstyringService ->
                    tilbakekrevingskomponenterMedClientStubs(
                        clock = clockParam,
                        sessionFactory = databaseRepos.sessionFactory,
                        hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
                        sakService = services.sak,
                        oppgaveService = services.oppgave,
                        oppgaveHendelseRepo = databaseRepos.oppgaveHendelseRepo,
                        mapRåttKravgrunnlagPåSakHendelse = mapRåttKravgrunnlagPåSakHendelse,
                        hendelseRepo = databaseRepos.hendelseRepo,
                        dokumentHendelseRepo = databaseRepos.dokumentHendelseRepo,
                        brevService = services.brev,
                        tilgangstyringService = tilgangstyringService,
                        sakStatistikkRepo = databaseRepos.sakStatistikkRepo,
                    )
                },
                tilgangstyringBuilder = { personService ->
                    TilgangstyringService(personService)
                },
                dokumentKomponenterBuilder = { databaseRepos, services, clients ->
                    val repos = no.nav.su.se.bakover.dokument.infrastructure.database.DokumentRepos(
                        clock = clockParam,
                        sessionFactory = databaseRepos.sessionFactory,
                        hendelseRepo = databaseRepos.hendelseRepo,
                        hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
                        dokumentHendelseRepo = databaseRepos.dokumentHendelseRepo,
                    )
                    Dokumentkomponenter(
                        repos = repos,
                        services = DokumentServices(
                            clock = clockParam,
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
                                clock = clockParam,
                            ),
                            distribuerDokumentHendelserKonsument = DistribuerDokumentHendelserKonsument(
                                sakService = services.sak,
                                dokDistFordeling = clients.dokDistFordeling,
                                hendelsekonsumenterRepo = repos.hendelsekonsumenterRepo,
                                dokumentHendelseRepo = repos.dokumentHendelseRepo,
                                sessionFactory = repos.sessionFactory,
                                clock = clockParam,
                            ),
                        ),
                    )
                },
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
    clientsBuilder: (databaseRepos: DatabaseRepos, clock: Clock, applicationConfig: ApplicationConfig) -> Clients = { databaseRepos, klokke, _applicationConfig ->
        TestClientsBuilder(
            clock = klokke,
            databaseRepos = databaseRepos,
        ).build(_applicationConfig)
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
                clock = klokke,
                satsFactory = satsFactoryIDag,
                formuegrenserFactory = formuegrenserFactoryIDag,
                applicationConfig = applicationConfig,
                dbMetrics = dbMetricsStub,
            )
        }
    },
    tilbakekrevingskomponenterBuilder: (databaseRepos: DatabaseRepos, services: Services, tilgangstyringService: TilgangstyringService) -> Tilbakekrevingskomponenter = { databaseRepos, services, tilgangstyringService ->
        Tilbakekrevingskomponenter.create(
            clock = clock,
            sessionFactory = databaseRepos.sessionFactory,
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
            tilgangstyringService = tilgangstyringService,
            sakStatistikkRepo = databaseRepos.sakStatistikkRepo,
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
    tilgangstyringBuilder: (personService: PersonService) -> TilgangstyringService = { personService ->
        TilgangstyringService(personService)
    },
    test: ApplicationTestBuilder.(appComponents: AppComponents) -> Unit,
) {
    withMigratedDb { dataSource ->
        testApplication(
            appComponents = AppComponents.instance(
                clock = clock,
                dataSource = dataSource,
                satsFactory = satsFactoryTest,
                repoBuilder = repoBuilder,
                clientBuilder = clientsBuilder,
                serviceBuilder = serviceBuilder,
                applicationConfig = applicationConfig,
                tilbakekrevingskomponenterBuilder = tilbakekrevingskomponenterBuilder,
                dokumentKomponenterBuilder = dokumentKomponenterBuilder,
                tilgangstyringBuilder = tilgangstyringBuilder,
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
        tilbakekrevingskomponenter = { _, _, _, _, _, _, _, _, _, _, _, _, _ ->
            appComponents.tilbakekrevingskomponenter
        },
        dokumentkomponenter = appComponents.dokumentHendelseKomponenter,
        disableConsumersAndJobs = true,
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
