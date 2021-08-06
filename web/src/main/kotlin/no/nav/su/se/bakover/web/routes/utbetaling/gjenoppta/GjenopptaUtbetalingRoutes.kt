package no.nav.su.se.bakover.web.routes.utbetaling.gjenoppta

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeGjenopptaUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withSakId

internal fun Route.gjenopptaUtbetalingRoutes(
    service: UtbetalingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$sakPath/{sakId}/utbetalinger/gjenoppta") {
            call.withSakId { sakId ->
                call.svar(
                    service.gjenopptaUtbetalinger(sakId, NavIdentBruker.Saksbehandler(call.suUserContext.navIdent))
                        .fold(
                            {
                                when (it) {
                                    KunneIkkeGjenopptaUtbetalinger.FantIkkeSak -> {
                                        NotFound.errorJson(
                                            "Fant ikke sak",
                                            "fant_ikke_sak",
                                        )
                                    }
                                    KunneIkkeGjenopptaUtbetalinger.HarIngenOversendteUtbetalinger -> {
                                        BadRequest.errorJson(
                                            "Ingen utbetalinger",
                                            "ingen_utbetalinger",
                                        )
                                    }
                                    KunneIkkeGjenopptaUtbetalinger.SisteUtbetalingErIkkeEnStansutbetaling -> {
                                        BadRequest.errorJson(
                                            "Siste utbetaling er ikke en stans",
                                            "siste_utbetaling_er_ikke_stans",
                                        )
                                    }
                                    KunneIkkeGjenopptaUtbetalinger.SimuleringAvStartutbetalingFeilet -> {
                                        InternalServerError.errorJson(
                                            "Simulering feilet",
                                            "simulering_feilet",
                                        )
                                    }
                                    KunneIkkeGjenopptaUtbetalinger.SendingAvUtbetalingTilOppdragFeilet -> {
                                        InternalServerError.errorJson(
                                            "Oversendelse til oppdrag feilet",
                                            "oversendelse_til_oppdrag_feilet",
                                        )
                                    }
                                    KunneIkkeGjenopptaUtbetalinger.KontrollAvSimuleringFeilet -> {
                                        InternalServerError.errorJson(
                                            "Kontroll av simulering feilet",
                                            "kontroll_av_simulering_feilet",
                                        )
                                    }
                                }
                            },
                            {
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, null)
                                call.sikkerlogg("Gjenopptar utbetaling på sak $sakId")
                                Resultat.json(OK, serialize(it.toJson()))
                            },
                        ),
                )
            }
        }
    }
}
