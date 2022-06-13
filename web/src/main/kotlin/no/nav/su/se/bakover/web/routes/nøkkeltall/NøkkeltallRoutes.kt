package no.nav.su.se.bakover.web.routes.nøkkeltall

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.svar

const val nøkkeltallPath = "/nøkkeltall"
internal fun Route.nøkkeltallRoutes(nøkkeltallService: NøkkeltallService) {
    get(nøkkeltallPath) {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Drift) {
            call.svar(Resultat.json(HttpStatusCode.OK, serialize(nøkkeltallService.hentNøkkeltall().toJson())))
        }
    }
}
