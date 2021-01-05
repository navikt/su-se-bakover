package no.nav.su.se.bakover.web

import com.nhaarman.mockitokotlin2.mock
import io.ktor.application.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.web.stubs.JwtStub
import no.nav.su.se.bakover.web.stubs.asBearerToken

const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

val applicationConfig = ApplicationConfig(
    isLocalOrRunningTests = true,
    leaderPodLookupPath = "leaderPodLookupPath",
    pdfgenLocal = false,
    corsAllowOrigin = "corsAllowOrigin",
    serviceUser = ApplicationConfig.ServiceUserConfig(
        username = "serviceUserTestUsername",
        password = "serviceUserTestPassword",
    ),
    azure = ApplicationConfig.AzureConfig(
        clientSecret = "testClientSecret",
        wellKnownUrl = "http://localhost/test/wellKnownUrl",
        clientId = "testClientId",
        backendCallbackUrl = "http://localhost/test/backendCallbackUrl",
        groups = ApplicationConfig.AzureConfig.AzureGroups(
            attestant = "testAzureGroupAttestant",
            saksbehandler = "testAzureGroupSaksbehandler",
            veileder = "testAzureGroupVeileder"
        )
    ),
    oppdrag = ApplicationConfig.OppdragConfig(
        mqQueueManager = "testMqQueueManager",
        mqPort = -22,
        mqHostname = "testMqHostname",
        mqChannel = "testMqChannel",
        utbetaling = ApplicationConfig.OppdragConfig.UtbetalingConfig(
            mqSendQueue = "testMqSendQueue",
            mqReplyTo = "testMqReplyTo"
        ),
        avstemming = ApplicationConfig.OppdragConfig.AvstemmingConfig(mqSendQueue = "avstemmingMqTestSendQueue"),
        simulering = ApplicationConfig.OppdragConfig.SimuleringConfig(
            url = "simuleringTestUrl",
            stsSoapUrl = "simuleringStsTestSoapUrl"
        )
    ),
    database = ApplicationConfig.DatabaseConfig.StaticCredentials(
        jdbcUrl = "jdbcTestUrl",
    ),
    clientsConfig = ApplicationConfig.ClientsConfig(
        oppgaveConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
            clientId = "oppgaveClientId",
            url = "oppgaveUrl"
        ),
        pdlUrl = "pdlUrl",
        dokDistUrl = "dokDistUrl",
        pdfgenUrl = "pdfgenUrl",
        dokarkivUrl = "dokarkivUrl",
        kodeverkUrl = "kodeverkUrl",
        stsUrl = "stsUrl",
        skjermingUrl = "skjermingUrl",
        dkifUrl = "dkifUrl",
    ),
    frontendCallbackUrls = ApplicationConfig.FrontendCallbackUrls(frontendBaseUrl = "frontendBaseUrl"),
    kafkaConfig = ApplicationConfig.KafkaConfig(emptyMap(), emptyMap())
)

internal val jwtStub = JwtStub(applicationConfig)

internal fun Application.testSusebakover(
    clients: Clients = TestClientsBuilder.build(applicationConfig),
    behandlingFactory: BehandlingFactory = BehandlingFactory(mock()),
    databaseRepos: DatabaseRepos = DatabaseBuilder.build(EmbeddedDatabase.instance(), behandlingFactory),
    services: Services = ServiceBuilder( // build actual clients
        databaseRepos = databaseRepos,
        clients = clients,
        behandlingMetrics = mock(),
        søknadMetrics = mock()
    ).build()
) {
    return susebakover(
        behandlingFactory = BehandlingFactory(mock()),
        databaseRepos = databaseRepos,
        clients = clients,
        services = services,
        applicationConfig = applicationConfig,
    )
}

fun TestApplicationEngine.defaultRequest(
    method: HttpMethod,
    uri: String,
    roller: List<Brukerrolle>,
    setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    return handleRequest(method, uri) {
        addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
        addHeader(HttpHeaders.Authorization, jwtStub.createJwtToken(roller = roller).asBearerToken())
        setup()
    }
}

fun TestApplicationEngine.requestSomAttestant(
    method: HttpMethod,
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    return handleRequest(method, uri) {
        addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
        addHeader(
            HttpHeaders.Authorization,
            jwtStub.createJwtToken(roller = listOf(Brukerrolle.Attestant)).asBearerToken()
        )
        setup()
    }
}
