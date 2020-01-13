package no.nav.su.se.bakover.nais

import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI

const val AZURE_CLIENT_ID = "clientId"
const val AZURE_CLIENT_SECRET = "secret"
const val AZURE_REQUIRED_GROUP = "su-group"
const val AZURE_WELL_KNOWN_URL = "/.well-known"
const val AZURE_JWKS_PATH = "/keys"
const val AZURE_ISSUER = "azure"
const val AZURE_BACKEND_CALLBACK_URL = "/callback"
const val AZURE_TOKEN_URL = "/token"
const val SUBJECT = "enSaksbehandler"
const val SU_PERSON_PATH = "/person"
const val SU_PERSON_AZURE_CLIENT_ID = "personClientId"
const val SU_INNTEKT_PATH = "/inntekt"
const val SU_INNTEKT_AZURE_CLIENT_ID = "inntektClientId"
const val SU_FRONTEND_REDIRECT_URL = "auth/complete"
const val SU_FRONTEND_ORIGIN = "localhost"
const val DEFAULT_CALL_ID = "callId"


@KtorExperimentalAPI
fun Application.testEnv(wireMockServer: WireMockServer? = null) {
    val baseUrl = wireMockServer?.let { it.baseUrl() } ?: SU_FRONTEND_ORIGIN
    (environment.config as MapApplicationConfig).apply {
        put("cors.allow.origin", SU_FRONTEND_ORIGIN)
        put("integrations.suPerson.url", "$baseUrl$SU_PERSON_PATH")
        put("integrations.suPerson.clientId", SU_PERSON_AZURE_CLIENT_ID)
        put("integrations.suInntekt.url", "$baseUrl$SU_INNTEKT_PATH")
        put("integrations.suInntekt.clientId", SU_INNTEKT_AZURE_CLIENT_ID)
        put("integrations.suSeFramover.redirectUrl", "$baseUrl$SU_FRONTEND_REDIRECT_URL")
        put("azure.requiredGroup", AZURE_REQUIRED_GROUP)
        put("azure.clientId", AZURE_CLIENT_ID)
        put("azure.clientSecret", AZURE_CLIENT_SECRET)
        put("azure.wellknownUrl", "$baseUrl$AZURE_WELL_KNOWN_URL")
        put("azure.backendCallbackUrl", "$baseUrl$AZURE_BACKEND_CALLBACK_URL")
        put("issuer", AZURE_ISSUER)
    }
}

fun TestApplicationEngine.withDefaultHeaders(
        method: HttpMethod,
        uri: String,
        setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    return handleRequest(method, uri) {
        addHeader(HttpHeaders.XRequestId, DEFAULT_CALL_ID)
        setup()
    }
}