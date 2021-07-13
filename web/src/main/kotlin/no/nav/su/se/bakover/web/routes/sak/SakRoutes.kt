package no.nav.su.se.bakover.web.routes.sak

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.ÅpenBehandlingJson.Companion.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId

internal const val sakPath = "/saker"

internal fun Route.sakRoutes(
    sakService: SakService,
) {
    post("$sakPath/søk") {
        data class Body(
            val fnr: String?,
            val saksnummer: String?,
        )
        call.withBody<Body> { body ->
            when {
                body.fnr != null -> {
                    Either.catch { Fnr(body.fnr) }.fold(
                        ifLeft = { call.svar(BadRequest.message("${body.fnr} er ikke et gyldig fødselsnummer")) },
                        ifRight = { fnr ->
                            sakService.hentSak(fnr)
                                .mapLeft {
                                    call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                                    call.svar(NotFound.message("Fant ikke noen sak for person: ${body.fnr}"))
                                }
                                .map {
                                    call.audit(fnr, AuditLogEvent.Action.ACCESS, null)
                                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                                }
                        },
                    )
                }
                body.saksnummer != null -> {
                    Saksnummer.tryParse(body.saksnummer).fold(
                        ifLeft = { call.svar(BadRequest.message("${body.saksnummer} er ikke et gyldig saksnummer")) },
                        ifRight = { saksnummer ->
                            call.svar(
                                sakService.hentSak(saksnummer).fold(
                                    { NotFound.message("Fant ikke sak med saksnummer: ${body.saksnummer}") },
                                    {
                                        call.audit(it.fnr, AuditLogEvent.Action.ACCESS, null)
                                        Resultat.json(OK, serialize((it.toJson())))
                                    },
                                ),
                            )
                        },
                    )
                }
                else -> call.svar(BadRequest.message("Må oppgi enten saksnummer eller fødselsnummer"))
            }
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
                    },
                ),
            )
        }
    }
    authorize(Brukerrolle.Saksbehandler) {
        get("$sakPath/") {
            val sakerMedÅpneBehandlinger = sakService.hentRestanserForAlleSaker()
            call.svar(Resultat.json(OK, serialize(sakerMedÅpneBehandlinger.toJson())))
        }
    }
}
