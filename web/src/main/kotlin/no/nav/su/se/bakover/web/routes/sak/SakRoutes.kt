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
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.SakRestansJson.Companion.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId

internal const val sakPath = "/saker"

internal fun Route.sakRoutes(
    sakService: SakService,
) {
    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        post("$sakPath/søk") {
            data class Body(
                val fnr: String?,
                val saksnummer: String?,
            )
            call.withBody<Body> { body ->
                when {
                    body.fnr != null -> {
                        Either.catch { Fnr(body.fnr) }.fold(
                            ifLeft = {
                                call.svar(
                                    BadRequest.errorJson(
                                        "${body.fnr} er ikke et gyldig fødselsnummer",
                                        "fnr_ikke_gyldig"
                                    )
                                )
                            },
                            ifRight = { fnr ->
                                sakService.hentSak(fnr)
                                    .mapLeft {
                                        call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                                        call.svar(
                                            NotFound.errorJson(
                                                "Fant ikke noen sak for person: ${body.fnr}",
                                                "fant_ikke_sak_for_person"
                                            )
                                        )
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
                            ifLeft = {
                                call.svar(
                                    BadRequest.errorJson(
                                        "${body.saksnummer} er ikke et gyldig saksnummer",
                                        "saksnummer_ikke_gyldig"
                                    )
                                )
                            },
                            ifRight = { saksnummer ->
                                call.svar(
                                    sakService.hentSak(saksnummer).fold(
                                        {
                                            NotFound.errorJson(
                                                "Fant ikke sak med saksnummer: ${body.saksnummer}",
                                                "fant_ikke_sak"
                                            )
                                        },
                                        {
                                            call.audit(it.fnr, AuditLogEvent.Action.ACCESS, null)
                                            Resultat.json(OK, serialize((it.toJson())))
                                        },
                                    ),
                                )
                            },
                        )
                    }
                    else -> call.svar(
                        BadRequest.errorJson(
                            "Må oppgi enten saksnummer eller fødselsnummer",
                            "mangler_saksnummer_fødselsnummer"
                        )
                    )
                }
            }
        }
    }

    get("$sakPath/{sakId}") {
        call.withSakId { sakId ->
            call.svar(
                sakService.hentSak(sakId).fold(
                    { NotFound.errorJson("Fant ikke sak med id: $sakId", "fant_ikke_sak") },
                    {
                        call.audit(it.fnr, AuditLogEvent.Action.ACCESS, null)
                        Resultat.json(OK, serialize((it.toJson())))
                    },
                ),
            )
        }
    }
    authorize(Brukerrolle.Saksbehandler) {
        get("$sakPath/restanser") {
            val restanser = sakService.hentRestanserForAlleSaker()
            call.svar(Resultat.json(OK, serialize(restanser.toJson())))
        }
    }
}
