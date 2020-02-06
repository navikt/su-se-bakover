package no.nav.su.se.bakover.inntekt

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.fromValue
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.Feil
import no.nav.su.se.bakover.Ok
import org.slf4j.LoggerFactory

private val sikkerLogg = LoggerFactory.getLogger("sikkerLogg")

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal fun Route.inntektRoutes(oppslag: InntektOppslag) {
    get<InntektPath> { inntektPath ->
        val principal = (call.authentication.principal as JWTPrincipal).payload
        sikkerLogg.info("${principal.subject} slår opp inntekt for person ${inntektPath.ident}")
        val response = oppslag.inntekt(
            ident = inntektPath.ident,
            innloggetSaksbehandlerToken = call.request.header(Authorization)!!,
            fomDato = inntektPath.fomDato,
            tomDato = inntektPath.tomDato
        )
        when (response) {
            is Ok -> call.respond(OK, response.json)
            is Feil -> call.respond(fromValue(response.httpCode), response.toJson())
        }
    }
}

@KtorExperimentalLocationsAPI
@Location("/inntekt")
internal data class InntektPath(val ident: String, val fomDato: String, val tomDato: String)
