package no.nav.su.se.bakover.utenlandsopphold.infrastructure.web

import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.web.features.authorize

internal fun Route.utenlandsoppholdRoutes() {
    get("/saker/{sakId}/utenlandsopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.svar(Resultat.okJson())
        }
    }
}
