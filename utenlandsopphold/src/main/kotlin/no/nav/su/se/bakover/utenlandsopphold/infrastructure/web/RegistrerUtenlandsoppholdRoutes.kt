package no.nav.su.se.bakover.utenlandsopphold.infrastructure.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.web.features.authorize

fun Route.utenlandsoppholdRoutes() {
    post("/saker/{sakId}/utenlandsopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId {
                call.withBody<RegistrerUtenlandsoppholdJson> { json ->
                    call.svar(Resultat.json(HttpStatusCode.Created, serialize(json)))
                }
            }
        }
    }
}
