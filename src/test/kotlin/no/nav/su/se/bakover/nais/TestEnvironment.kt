package no.nav.su.se.bakover.nais

import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.util.KtorExperimentalAPI

const val AZURE_CLIENT_ID = "clientId"
const val AZURE_REQUIRED_GROUP = "su-group"
const val AZURE_WELL_KNOWN_URL = "/.well-known"
const val AZURE_JWKS_PATH = "/keys"
const val AZURE_ISSUER = "azure"
const val AZURE_TENANT = "tenant"
const val SUBJECT = "enSaksbehandler"
const val SU_PERSON_PATH = "/person"
const val SU_INNTEKT_PATH = "/inntekt"
const val HTTP_LOCALHOST = "http://localhost"

@KtorExperimentalAPI
fun Application.testEnv(wireMockServer: WireMockServer? = null) {
    val baseUrl = wireMockServer?.let { it.baseUrl() } ?: HTTP_LOCALHOST
    (environment.config as MapApplicationConfig).apply {
        put("allowCorsOrigin", HTTP_LOCALHOST)
        put("integrations.suPerson.url", "$baseUrl$SU_PERSON_PATH")
        put("integrations.suInntekt.url", "$baseUrl$SU_INNTEKT_PATH")
        put("azure.tenant", AZURE_TENANT)
        put("azure.requiredGroup", AZURE_REQUIRED_GROUP)
        put("azure.clientId", AZURE_CLIENT_ID)
        put("azure.wellknownUrl", "$baseUrl$AZURE_WELL_KNOWN_URL")
        put("issuer", AZURE_ISSUER)
    }
}