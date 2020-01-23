package no.nav.su.se.bakover.inntekt

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.config.ApplicationConfig
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.getProperty
import org.slf4j.LoggerFactory

private val sikkerLogg = LoggerFactory.getLogger("sikkerLogg")

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
fun Route.inntektRoutes(config: ApplicationConfig, azureClient: AzureClient, inntektClient: SuInntektClient) {
    get<InntektPath> { inntektPath ->
        val principal = (call.authentication.principal as JWTPrincipal).payload
        sikkerLogg.info("${principal.subject} slår opp inntekt for person ${inntektPath.ident}")
        val suInntektToken = azureClient.onBehalfOFToken(call.request.header(Authorization)!!, config.getProperty("integrations.suInntekt.clientId"))
        call.respond(inntektClient.inntekt(ident = inntektPath.ident, suInntektToken = suInntektToken, fomDato = inntektPath.fomDato, tomDato = inntektPath.tomDato))
    }
}

@KtorExperimentalLocationsAPI
@Location("/inntekt")
data class InntektPath(val ident: String, val fomDato: String, val tomDato: String)