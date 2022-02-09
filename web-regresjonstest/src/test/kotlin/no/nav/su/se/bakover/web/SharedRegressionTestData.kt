package no.nav.su.se.bakover.web

import io.ktor.application.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
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
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.stubs.JwtStub
import no.nav.su.se.bakover.web.stubs.asBearerToken
import org.mockito.kotlin.mock
import java.time.Clock
import javax.sql.DataSource

/**
 * TODO jah: Dette er foreløpig en kopi av TestEnvironment.kt og TestClientsBuilder.kt fra web/src/test (på sikt bør det meste av dette slettes derfra)
 * Vurder å trekk ut ting til test-common for de tingene som både web og web-regresjonstest trenger.
 */
object SharedRegressionTestData {
    val fnr: String = Fnr.generer().toString()

    private const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

    private val applicationConfig = ApplicationConfig(
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
        jobConfig = ApplicationConfig.JobConfig(
            personhendelse = ApplicationConfig.JobConfig.Personhendelse(null),
            konsistensavstemming = ApplicationConfig.JobConfig.Konsistensavstemming.Local(),
            initialDelay = 0
        ),
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
    ): DatabaseRepos {
        return DatabaseBuilder.build(
            embeddedDatasource = dataSource,
            dbMetrics = dbMetricsStub,
            clock = clock,
        )
    }

    internal fun Application.testSusebakover(
        clock: Clock = fixedClock,
        databaseRepos: DatabaseRepos = databaseRepos(clock = clock),
        clients: Clients = TestClientsBuilder(clock, databaseRepos).build(applicationConfig),
        unleash: Unleash = FakeUnleash().apply { enableAll() },
        services: Services = ServiceBuilder.build(
            databaseRepos = databaseRepos,
            clients = clients,
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = clock,
            unleash = unleash,
        ),
        accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
    ) {
        return susebakover(
            databaseRepos = databaseRepos,
            clients = clients,
            services = services,
            accessCheckProxy = accessCheckProxy,
            applicationConfig = applicationConfig,
            clock = clock,
        )
    }

    fun TestApplicationEngine.defaultRequest(
        method: HttpMethod,
        uri: String,
        roller: List<Brukerrolle>,
        setup: TestApplicationRequest.() -> Unit = {},
    ): TestApplicationCall {
        return handleRequest(method, uri) {
            addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
            addHeader(HttpHeaders.Authorization, jwtStub.createJwtToken(roller = roller).asBearerToken())
            setup()
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
        simuleringClient = SimuleringStub(fixedClock, databaseRepos.utbetaling),
        utbetalingPublisher = UtbetalingStub,
        dokDistFordeling = DokDistFordelingStub,
        avstemmingPublisher = AvstemmingStub,
        identClient = IdentClientStub,
        digitalKontaktinformasjon = DkifClientStub,
        leaderPodLookup = LeaderPodLookupStub,
        kafkaPublisher = KafkaPublisherStub,
        klageClient = KlageClientStub,
        journalpostClient = JournalpostClientStub
    )

    override fun build(applicationConfig: ApplicationConfig): Clients = testClients
}
