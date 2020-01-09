package no.nav.su.se.bakover.nais

import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.util.KtorExperimentalAPI

const val AZURE_CLIENT_ID = "clientId"
const val AZURE_CLIENT_SECRET = "secret"
const val OIDC_REQUIRED_GROUP = "su-group"
const val AZURE_WELL_KNOWN_URL = "/.well-known"
const val AZURE_JWKS_PATH = "/keys"
const val AZURE_ISSUER = "azure"
const val AZURE_TENANT = "tenant"
const val AZURE_BACKEND_CALLBACK_URL = "/callback"
const val SUBJECT = "enSaksbehandler"
const val SU_PERSON_PATH = "/person"
const val SU_INNTEKT_PATH = "/inntekt"
const val SU_FRONTEND_REDIRECT_URL = "auth/complete"
const val SU_FRONTEND_ORIGIN = "localhost"

@KtorExperimentalAPI
fun Application.testEnv(wireMockServer: WireMockServer? = null) {
    val baseUrl = wireMockServer?.let { it.baseUrl() } ?: SU_FRONTEND_ORIGIN
    (environment.config as MapApplicationConfig).apply {
        put("cors.allow.origin", SU_FRONTEND_ORIGIN)
        put("integrations.suPerson.url", "$baseUrl$SU_PERSON_PATH")
        put("integrations.suInntekt.url", "$baseUrl$SU_INNTEKT_PATH")
        put("integrations.suSeFramover.redirectUrl", "$baseUrl$SU_FRONTEND_REDIRECT_URL")
        put("azure.tenant", AZURE_TENANT)
        put("azure.requiredGroup", OIDC_REQUIRED_GROUP)
        put("azure.clientId", AZURE_CLIENT_ID)
        put("azure.clientSecret", AZURE_CLIENT_SECRET)
        put("azure.wellknownUrl", "$baseUrl$AZURE_WELL_KNOWN_URL")
        put("azure.backendCallbackUrl", "$baseUrl$AZURE_BACKEND_CALLBACK_URL")
        put("issuer", AZURE_ISSUER)
    }
}