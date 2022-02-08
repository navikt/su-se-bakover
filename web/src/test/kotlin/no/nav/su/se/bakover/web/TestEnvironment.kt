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
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.migratedDb
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.web.stubs.JwtStub
import no.nav.su.se.bakover.web.stubs.asBearerToken
import org.mockito.kotlin.mock
import java.time.Clock

const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

val applicationConfig = ApplicationConfig(
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
                mqReplyTo = "tilbakekrevingMqTestSendQueue",
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
        kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig("kabalUrl", "kabalClientId"),
        safConfig = ApplicationConfig.ClientsConfig.SafConfig("safUrlkabalUrl", "safClientId"),
    ),
    kafkaConfig = ApplicationConfig.KafkaConfig(
        producerCfg = ApplicationConfig.KafkaConfig.ProducerCfg(emptyMap()),
        consumerCfg = ApplicationConfig.KafkaConfig.ConsumerCfg(emptyMap()),
    ),
    unleash = ApplicationConfig.UnleashConfig("https://localhost", "su-se-bakover"),
    jobConfig = ApplicationConfig.JobConfig(
        personhendelse = ApplicationConfig.JobConfig.Personhendelse(null),
        konsistensavstemming = ApplicationConfig.JobConfig.Konsistensavstemming.Local(),
    ),
    kabalKafkaConfig = ApplicationConfig.KabalKafkaConfig(
        kafkaConfig = emptyMap(),
    ),
)

internal val jwtStub = JwtStub(applicationConfig.azure)

internal val dbMetricsStub: DbMetrics = object : DbMetrics {
    override fun <T> timeQuery(label: String, block: () -> T): T {
        return block()
    }
}

internal fun mockedDb() = TestDatabaseBuilder.build()
internal fun embeddedPostgres(clock: Clock = fixedClock) = DatabaseBuilder.build(
    embeddedDatasource = migratedDb(),
    dbMetrics = dbMetricsStub,
    clock = clock,
)

internal fun Application.testSusebakover(
    clock: Clock = fixedClock,
    databaseRepos: DatabaseRepos = mockedDb(),
    clients: Clients = TestClientsBuilder(clock, databaseRepos).build(applicationConfig),
    unleash: Unleash = FakeUnleash().apply { enableAll() },
    services: Services = ServiceBuilder.build(
        // build actual clients
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

fun TestApplicationEngine.defaultRequest(
    method: HttpMethod,
    uri: String,
    roller: List<Brukerrolle>,
    navIdent: String,
    setup: TestApplicationRequest.() -> Unit = {},
): TestApplicationCall {
    return handleRequest(method, uri) {
        addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
        addHeader(
            HttpHeaders.Authorization,
            jwtStub.createJwtToken(roller = roller, navIdent = navIdent).asBearerToken(),
        )
        setup()
    }
}

fun TestApplicationEngine.requestSomAttestant(
    method: HttpMethod,
    uri: String,
    navIdent: String? = null,
    setup: TestApplicationRequest.() -> Unit = {},
): TestApplicationCall {
    return handleRequest(method, uri) {
        addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
        addHeader(
            HttpHeaders.Authorization,
            jwtStub.createJwtToken(roller = listOf(Brukerrolle.Attestant), navIdent = navIdent).asBearerToken(),
        )
        setup()
    }
}

fun TestApplicationEngine.requestSomAttestant(
    method: HttpMethod,
    uri: String,
): TestApplicationCall {
    return requestSomAttestant(method, uri, null) {}
}
