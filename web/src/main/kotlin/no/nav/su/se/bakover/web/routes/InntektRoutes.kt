package no.nav.su.se.bakover.web.routes

import io.ktor.application.call
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.request.header
import io.ktor.routing.Route
import no.nav.su.se.bakover.client.inntekt.InntektOppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.svar

@OptIn(io.ktor.locations.KtorExperimentalLocationsAPI::class)
internal fun Route.inntektRoutes(oppslag: InntektOppslag) {
    get<InntektPath> { inntektPath ->
        call.audit("slår opp inntekt for person: ${inntektPath.ident}")
        val resultat = oppslag.inntekt(
            ident = Fnr(inntektPath.ident),
            innloggetSaksbehandlerToken = call.request.header(Authorization)!!,
            fraOgMedDato = inntektPath.fraOgMedDato,
            tilOgMedDato = inntektPath.tilOgMedDato
        )
        call.svar(Resultat.from(resultat))
    }
}

@OptIn(io.ktor.locations.KtorExperimentalLocationsAPI::class)
@Location("/inntekt")
internal data class InntektPath(val ident: String, internal val fraOgMedDato: String, val tilOgMedDato: String)
