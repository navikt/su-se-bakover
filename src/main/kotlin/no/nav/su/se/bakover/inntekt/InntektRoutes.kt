package no.nav.su.se.bakover.inntekt

import io.ktor.application.call
import io.ktor.config.ApplicationConfig
import io.ktor.http.HttpHeaders
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.getProperty

const val inntektPath = "/inntekt"
const val identLabel = "ident"

@KtorExperimentalAPI
fun Route.inntektRoutes(config: ApplicationConfig, azureClient: AzureClient, inntektClient: SuInntektClient) {
    get(path = inntektPath) {
        val suInntektToken = azureClient.onBehalfOFToken(call.request.header(HttpHeaders.Authorization)!!, config.getProperty("integrations.suInntekt.clientId"))
        call.respond(inntektClient.inntekt(ident = call.parameters[identLabel]!!, suInntektToken = suInntektToken))
    }
}