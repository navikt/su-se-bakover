package no.nav.su.se.bakover.utenlandsopphold.infrastructure.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.lesUUID
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.web.features.authorize
import java.util.UUID

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

    put("/saker/{sakId}/utenlandsopphold/{utenlandsoppholdId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withUtenlandsoppholdId {
                call.withBody<RegistrerUtenlandsoppholdJson> { json ->
                    call.svar(Resultat.json(HttpStatusCode.OK, serialize(json)))
                }
            }
        }
    }
}

private suspend fun ApplicationCall.withUtenlandsoppholdId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("utenlandsoppholdId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "utenlandsoppholdId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}
