package no.nav.su.se.bakover.utenlandsopphold.presentation.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.getCorrelationId
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.lesUUID
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.utenlandsopphold.application.RegistrerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.presentation.web.RegistrerteUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.web.features.authorize
import java.util.UUID

fun Route.utenlandsoppholdRoutes(
    service: RegistrerUtenlandsoppholdService,
) {
    post("/saker/{sakId}/utenlandsopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<RegistrerUtenlandsoppholdJson> { json ->
                    service.registrer(
                        json.toCommand(
                            sakId = sakId,
                            opprettetAv = call.suUserContext.saksbehandler,
                            correlationId = getCorrelationId()!!,
                        ),
                    ).tap {
                        call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                    }.tapLeft {
                        call.svar(
                            HttpStatusCode.BadRequest.errorJson(
                                message = "Ønsket periode overlapper med tidligere perioder",
                                code = "overlappende_perioder",
                            ),
                        )
                    }
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

    delete("/saker/{sakId}/utenlandsopphold/{utenlandsoppholdId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withUtenlandsoppholdId {
                call.svar(Resultat.json(HttpStatusCode.OK, """{"utenlandsoppholdId":"$it"}"""))
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
