package no.nav.su.se.bakover.web.routes.nøkkeltall

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallService

const val NØKKELTALL_PATH = "/nøkkeltall"
internal fun Route.nøkkeltallRoutes(nøkkeltallService: NøkkeltallService) {
    get(NØKKELTALL_PATH) {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Drift) {
            call.svar(Resultat.json(HttpStatusCode.OK, serialize(nøkkeltallService.hentNøkkeltall().toJson())))
        }
    }
}
