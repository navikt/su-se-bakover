package no.nav.su.se.bakover.web

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.nhaarman.mockitokotlin2.mock
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
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
import java.util.Base64

const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

val applicationConfig = ApplicationConfig(
    isLocalOrRunningTests = true,
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
    database = ApplicationConfig.DatabaseConfig(
        databaseName = "databaseTestname",
        jdbcUrl = "jdbcTestUrl",
        vaultMountPath = "",
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
)

fun toAzureTestGroup(rolle: Brukerrolle) =
    when (rolle) {
        Brukerrolle.Attestant -> applicationConfig.azure.groups.attestant
        Brukerrolle.Saksbehandler -> applicationConfig.azure.groups.saksbehandler
        Brukerrolle.Veileder -> applicationConfig.azure.groups.veileder
    }

fun authenticationHttpClient() = HttpClient(MockEngine) {
    followRedirects = false
    engine {
        addHandler {
            val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Text.Plain.toString()))
            //language=JSON
            respond(
                """
                {
                    "access_token":"access",
                    "refresh_token":"refresh"
                }
                """.trimIndent(),
                headers = responseHeaders
            )
        }
    }
}

internal fun Application.testSusebakover(
    clients: Clients = TestClientsBuilder.build(),
    jwkProvider: JwkProvider = JwkProviderStub,
    behandlingFactory: BehandlingFactory = BehandlingFactory(mock()),
    databaseRepos: DatabaseRepos = DatabaseBuilder.build(EmbeddedDatabase.instance(), behandlingFactory),
    authenticationHttpClient: HttpClient = authenticationHttpClient(),
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
        jwkProvider = jwkProvider,
        authenticationHttpClient = authenticationHttpClient,
        services = services,
        applicationConfig = applicationConfig,
    )
}

internal object JwkProviderStub : JwkProvider {
    override fun get(keyId: String?) = Jwk(
        "key-1234",
        "RSA",
        "RS256",
        null,
        emptyList(),
        null,
        null,
        null,
        mapOf(
            "e" to String(Base64.getEncoder().encode(Jwt.keys.first.publicExponent.toByteArray())),
            "n" to String(Base64.getEncoder().encode(Jwt.keys.first.modulus.toByteArray()))
        )
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
        addHeader(HttpHeaders.Authorization, Jwt.create(roller = roller))
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
            Jwt.create(roller = listOf(Brukerrolle.Attestant))
        )
        setup()
    }
}
