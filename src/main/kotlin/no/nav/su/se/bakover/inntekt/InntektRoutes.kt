package no.nav.su.se.bakover.inntekt

import io.ktor.application.call
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.config.ApplicationConfig
import io.ktor.http.*
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.getProperty
import org.slf4j.*

const val inntektPath = "/inntekt"
const val identLabel = "ident"

private val sikkerLogg = LoggerFactory.getLogger("sikkerLogg")

@KtorExperimentalAPI
fun Route.inntektRoutes(config: ApplicationConfig, azureClient: AzureClient, inntektClient: SuInntektClient) {
    get(path = inntektPath) {
        call.parameters[identLabel]?.let { personIdent ->
            val principal = (call.authentication.principal as JWTPrincipal).payload
            sikkerLogg.info("${principal.subject} slår opp inntekt for person $personIdent")
            val suInntektToken = azureClient.onBehalfOFToken(call.request.header(HttpHeaders.Authorization)!!,
                    config.getProperty("integrations.suInntekt.clientId"))
            call.respond(inntektClient.inntekt(ident = personIdent, suInntektToken = suInntektToken))
            } ?: call.respond(HttpStatusCode.BadRequest, "query param '${identLabel}' må oppgis")
    }
}