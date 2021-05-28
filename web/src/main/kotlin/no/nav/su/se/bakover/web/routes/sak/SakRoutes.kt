package no.nav.su.se.bakover.web.routes.sak

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.lesFnr
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withSakId
import no.nav.su.se.bakover.web.withSaksnummer

internal const val sakPath = "/saker"

internal fun Route.sakRoutes(
    sakService: SakService,
) {
    get(sakPath) {
        when {
            call.parameters.contains("saksnummer") -> {
                call.withSaksnummer { saksnummer ->
                    call.svar(
                        sakService.hentSak(saksnummer).fold(
                            { NotFound.message("Fant ikke sak med saksnummer: $saksnummer") },
                            {
                                call.audit(it.fnr, AuditLogEvent.Action.ACCESS, null)
                                Resultat.json(OK, serialize((it.toJson())))
                            }
                        )
                    )
                }
            }
            call.parameters.contains("fnr") -> {
                call.lesFnr("fnr").fold(
                    ifLeft = { call.svar(BadRequest.message(it)) },
                    ifRight = { fnr ->
                        sakService.hentSak(fnr)
                            .mapLeft {
                                call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                                call.svar(NotFound.message("Fant ikke noen sak for person: $fnr"))
                            }
                            .map {
                                call.audit(fnr, AuditLogEvent.Action.ACCESS, null)
                                call.svar(Resultat.json(OK, serialize((it.toJson()))))
                            }
                    }
                )
            }
            else -> call.svar(BadRequest.message("Må oppgi saksnummer eller fødselsnummer"))
        }
    }

    get("$sakPath/{sakId}") {
        call.withSakId { sakId ->
            call.svar(
                sakService.hentSak(sakId).fold(
                    { NotFound.message("Fant ikke sak med id: $sakId") },
                    {
                        call.audit(it.fnr, AuditLogEvent.Action.ACCESS, null)
                        Resultat.json(OK, serialize((it.toJson())))
                    }
                )
            )
        }
    }
}
