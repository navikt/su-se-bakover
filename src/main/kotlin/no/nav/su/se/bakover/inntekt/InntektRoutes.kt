package no.nav.su.se.bakover.inntekt

import io.ktor.application.call
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.request.header
import io.ktor.routing.Route
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.ContextHolder.SecurityContext

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal fun Route.inntektRoutes(oppslag: InntektOppslag) {
    get<InntektPath> { inntektPath ->
        launchWithContext(SecurityContext(call.authHeader())) {
            call.audit("slår opp inntekt for person: ${inntektPath.ident}")
            val resultat = oppslag.inntekt(
                    ident = Fødselsnummer(inntektPath.ident),
                    innloggetSaksbehandlerToken = call.request.header(Authorization)!!,
                    fomDato = inntektPath.fomDato,
                    tomDato = inntektPath.tomDato
            )
            call.svar(resultat)
        }
    }
}

@KtorExperimentalLocationsAPI
@Location("/inntekt")
internal data class InntektPath(val ident: String, val fomDato: String, val tomDato: String)
