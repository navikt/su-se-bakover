package no.nav.su.se.bakover.web.routes.skatt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.svar

internal const val skattPath = "/skatt"

internal fun Route.skattRoutes(skatteService: SkatteService) {
    post("$skattPath/test") {
        authorize(Brukerrolle.Saksbehandler) {
            skatteService.hentSkattemelding(Fnr("12345678901"))
                .fold(
                    ifLeft = { Feilresponser.fantIkkeVedtak.svar(call) },
                    ifRight = {
                        call.svar(Resultat.json(HttpStatusCode.OK, it.toString()))
                    }
                )
        }
    }
}
