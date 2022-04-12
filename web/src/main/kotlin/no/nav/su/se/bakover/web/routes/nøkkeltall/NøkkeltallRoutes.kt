package no.nav.su.se.bakover.web.routes.nøkkeltall

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallService
import no.nav.su.se.bakover.web.features.authorize

const val nøkkeltallPath = "/nøkkeltall"
internal fun Route.nøkkeltallRoutes(nøkkeltallService: NøkkeltallService) {
    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Drift) {
        get(nøkkeltallPath) {
            call.respond(HttpStatusCode.OK, nøkkeltallService.hentNøkkeltall().toJson())
        }
    }
}
