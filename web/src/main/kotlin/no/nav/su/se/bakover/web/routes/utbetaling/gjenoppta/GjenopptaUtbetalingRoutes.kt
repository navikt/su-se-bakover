package no.nav.su.se.bakover.web.routes.utbetaling.gjenoppta

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeGjenopptaUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.withSakId

@KtorExperimentalAPI
internal fun Route.gjenopptaUtbetalingRoutes(
    service: UtbetalingService
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$sakPath/{sakId}/utbetalinger/gjenoppta") {
            call.withSakId { sakId ->
                service.gjenopptaUtbetalinger(sakId, NavIdentBruker.Saksbehandler(call.suUserContext.navIdent))
                    .fold(
                        {
                            when (it) {
                                KunneIkkeGjenopptaUtbetalinger.FantIkkeSak -> call.respond(NotFound, "Fant ikke sak")
                                KunneIkkeGjenopptaUtbetalinger.HarIngenOversendteUtbetalinger -> call.respond(
                                    BadRequest,
                                    "Ingen utbetalinger"
                                )
                                KunneIkkeGjenopptaUtbetalinger.SisteUtbetalingErIkkeEnStansutbetaling -> call.respond(
                                    BadRequest,
                                    "Siste utbetaling er ikke en stans"
                                )
                                KunneIkkeGjenopptaUtbetalinger.SimuleringAvStartutbetalingFeilet -> call.respond(
                                    InternalServerError,
                                    "Simulering feilet"
                                )
                                KunneIkkeGjenopptaUtbetalinger.SendingAvUtebetalingTilOppdragFeilet -> call.respond(
                                    InternalServerError,
                                    "Oversendelse til oppdrag feilet"
                                )
                            }
                        },
                        {
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, null)
                            call.sikkerlogg("Gjenopptar utbetaling på sak $sakId")
                            call.respond(serialize(it.toJson()))
                        }
                    )
            }
        }
    }
}
