package no.nav.su.se.bakover.web

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import no.finn.unleash.FakeUnleash
import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ClientsBuilder
import no.nav.su.se.bakover.client.journalpost.JournalpostClientStub
import no.nav.su.se.bakover.client.kabal.KlageClientStub
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.client.stubs.dkif.DkifClientStub
import no.nav.su.se.bakover.client.stubs.dokarkiv.DokArkivStub
import no.nav.su.se.bakover.client.stubs.dokdistfordeling.DokDistFordelingStub
import no.nav.su.se.bakover.client.stubs.kafka.KafkaPublisherStub
import no.nav.su.se.bakover.client.stubs.nais.LeaderPodLookupStub
import no.nav.su.se.bakover.client.stubs.oppdrag.AvstemmingStub
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.client.stubs.oppdrag.TilbakekrevingClientStub
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.IdentClientStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.migratedDb
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.web.stubs.JwtStub
import no.nav.su.se.bakover.web.stubs.asBearerToken
import org.mockito.kotlin.mock
import java.time.Clock
import javax.sql.DataSource

/**
 * TODO jah: Dette er foreløpig en kopi av TestEnvironment.kt og TestClientsBuilder.kt fra web/src/test (på sikt bør det meste av dette slettes derfra)
 * Vurder å trekk ut ting til test-common for de tingene som både web og web-regresjonstest trenger.
 */
internal object SharedRegressionTestData {
    internal val fnr: String = Fnr.generer().toString()

    private const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

    internal val applicationConfig = ApplicationConfig(
        runtimeEnvironment = ApplicationConfig.RuntimeEnvironment.Test,
        naisCluster = null,
        leaderPodLookupPath = "leaderPodLookupPath",
        pdfgenLocal = false,
        serviceUser = ApplicationConfig.ServiceUserConfig(
            username = "serviceUserTestUsername",
            password = "serviceUserTestPassword",
        ),
        azure = ApplicationConfig.AzureConfig(
            clientSecret = "testClientSecret",
            wellKnownUrl = "http://localhost/test/wellKnownUrl",
            clientId = "testClientId",
            groups = ApplicationConfig.AzureConfig.AzureGroups(
                attestant = "testAzureGroupAttestant",
                saksbehandler = "testAzureGroupSaksbehandler",
                veileder = "testAzureGroupVeileder",
                drift = "testAzureGroupDrift",
            ),
        ),
        frikort = ApplicationConfig.FrikortConfig(
            serviceUsername = "frikort",
            useStubForSts = true,
        ),
        oppdrag = ApplicationConfig.OppdragConfig(
            mqQueueManager = "testMqQueueManager",
            mqPort = -22,
            mqHostname = "testMqHostname",
            mqChannel = "testMqChannel",
            utbetaling = ApplicationConfig.OppdragConfig.UtbetalingConfig(
                mqSendQueue = "testMqSendQueue",
                mqReplyTo = "testMqReplyTo",
            ),
            avstemming = ApplicationConfig.OppdragConfig.AvstemmingConfig(mqSendQueue = "avstemmingMqTestSendQueue"),
            simulering = ApplicationConfig.OppdragConfig.SimuleringConfig(
                url = "simuleringTestUrl",
                stsSoapUrl = "simuleringStsTestSoapUrl",
            ),
            tilbakekreving = ApplicationConfig.OppdragConfig.TilbakekrevingConfig(
                mq = ApplicationConfig.OppdragConfig.TilbakekrevingConfig.Mq(
                    mottak = "tilbakekrevingMqTestSendQueue",
                ),
                soap = ApplicationConfig.OppdragConfig.TilbakekrevingConfig.Soap(
                    url = "tilbakekrevingUrl",
                ),
            ),
        ),
        database = ApplicationConfig.DatabaseConfig.StaticCredentials(
            jdbcUrl = "jdbcTestUrl",
        ),
        clientsConfig = ApplicationConfig.ClientsConfig(
            oppgaveConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = "oppgaveUrl",
            ),
            pdlConfig = ApplicationConfig.ClientsConfig.PdlConfig(
                url = "pdlUrl",
                clientId = "pdlClientId",
            ),
            dokDistUrl = "dokDistUrl",
            pdfgenUrl = "pdfgenUrl",
            dokarkivUrl = "dokarkivUrl",
            kodeverkUrl = "kodeverkUrl",
            stsUrl = "stsUrl",
            skjermingUrl = "skjermingUrl",
            dkifUrl = "dkifUrl",
            kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig(url = "kabalUrl", clientId = "KabalClientId"),
            safConfig = ApplicationConfig.ClientsConfig.SafConfig(url = "safUrl", clientId = "safClientId"),
        ),
        kafkaConfig = ApplicationConfig.KafkaConfig(
            producerCfg = ApplicationConfig.KafkaConfig.ProducerCfg(emptyMap()),
            consumerCfg = ApplicationConfig.KafkaConfig.ConsumerCfg(emptyMap()),
        ),
        unleash = ApplicationConfig.UnleashConfig("https://localhost", "su-se-bakover"),
        kabalKafkaConfig = ApplicationConfig.KabalKafkaConfig(
            kafkaConfig = emptyMap(),
        ),
    )

    private val jwtStub = JwtStub(applicationConfig.azure)

    private val dbMetricsStub: DbMetrics = object : DbMetrics {
        override fun <T> timeQuery(label: String, block: () -> T): T {
            return block()
        }
    }

    internal fun databaseRepos(
        dataSource: DataSource = migratedDb(),
        clock: Clock = fixedClock,
        satsFactory: SatsFactory = satsFactoryTest(clock),
    ): DatabaseRepos {
        return DatabaseBuilder.build(
            embeddedDatasource = dataSource,
            dbMetrics = dbMetricsStub,
            clock = clock,
            satsFactory = satsFactory,
        )
    }

    /**
     * Uses the local docker-database as datasource.
     * @param clock defaults to system UTC
     */
    internal fun withTestApplicationAndDockerDb(
        clock: Clock = Clock.systemUTC(),
        test: ApplicationTestBuilder.() -> Unit,
    ) {
        val dataSource = DatabaseBuilder.newLocalDataSource()
        DatabaseBuilder.migrateDatabase(dataSource)

        testApplication {
            application {
                testSusebakover(
                    clock = clock,
                    databaseRepos = databaseRepos(
                        dataSource = dataSource,
                        clock = clock,
                    ),
                )
            }
            test()
        }
    }

    internal fun withTestApplicationAndEmbeddedDb(
        clock: Clock = fixedClock,
        test: ApplicationTestBuilder.() -> Unit,
    ) {
        withMigratedDb { dataSource ->
            testApplication {
                application {
                    testSusebakover(
                        clock = clock,
                        databaseRepos = databaseRepos(
                            dataSource = dataSource,
                            clock = clock,
                        ),
                    )
                }
                test()
            }
        }
    }

    private fun Application.testSusebakover(
        clock: Clock = fixedClock,
        satsFactory: SatsFactory = satsFactoryTest(clock),
        databaseRepos: DatabaseRepos = databaseRepos(
            clock = clock,
            satsFactory = satsFactory,
        ),
        clients: Clients = TestClientsBuilder(clock, databaseRepos).build(applicationConfig),
        unleash: Unleash = FakeUnleash().apply { enableAll() },
        services: Services = ServiceBuilder.build(
            databaseRepos = databaseRepos,
            clients = clients,
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = clock,
            unleash = unleash,
            satsFactory = satsFactory,
        ),
        accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
    ) {
        return susebakover(
            clock = clock,
            applicationConfig = applicationConfig,
            databaseRepos = databaseRepos,
            clients = clients,
            services = services,
            accessCheckProxy = accessCheckProxy,
        )
    }

    fun ApplicationTestBuilder.defaultRequest(
        method: HttpMethod,
        uri: String,
        roller: List<Brukerrolle> = emptyList(),
        navIdent: String = "Z990Lokal",
        setup: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        return runBlocking {
            client.request(uri) {
                this.method = method
                this.headers {
                    append(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                    append(
                        HttpHeaders.Authorization,
                        jwtStub.createJwtToken(roller = roller, navIdent = navIdent).asBearerToken(),
                    )
                }
                setup()
            }
        }
    }
}

data class TestClientsBuilder(
    val clock: Clock,
    val databaseRepos: DatabaseRepos,
) : ClientsBuilder {
    private val testClients = Clients(
        oauth = AzureClientStub,
        personOppslag = PersonOppslagStub,
        tokenOppslag = TokenOppslagStub,
        pdfGenerator = PdfGeneratorStub,
        dokArkiv = DokArkivStub,
        oppgaveClient = OppgaveClientStub,
        kodeverk = mock(),
        simuleringClient = SimuleringStub(clock, databaseRepos.utbetaling),
        utbetalingPublisher = UtbetalingStub,
        dokDistFordeling = DokDistFordelingStub,
        avstemmingPublisher = AvstemmingStub,
        identClient = IdentClientStub,
        digitalKontaktinformasjon = DkifClientStub,
        leaderPodLookup = LeaderPodLookupStub,
        kafkaPublisher = KafkaPublisherStub,
        klageClient = KlageClientStub,
        journalpostClient = JournalpostClientStub,
        tilbakekrevingClient = TilbakekrevingClientStub(clock),
    )

    override fun build(applicationConfig: ApplicationConfig): Clients = testClients
}
