package no.nav.su.se.bakover.web.routes.utbetaling.stans

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeStanseUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.withSakId

internal fun Route.stansutbetalingRoutes(
    utbetalingService: UtbetalingService
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$sakPath/{sakId}/utbetalinger/stans") {
            call.withSakId { sakId ->
                utbetalingService.stansUtbetalinger(
                    sakId = sakId,
                    saksbehandler = call.suUserContext.navIdent.let { Saksbehandler(it) }
                ).fold(
                    {
                        when (it) {
                            KunneIkkeStanseUtbetalinger.FantIkkeSak ->
                                call.respond(NotFound, "Fant ikke sak")
                            KunneIkkeStanseUtbetalinger.SimuleringAvStansFeilet ->
                                call.respond(InternalServerError, "Simulering av stans feilet")
                            KunneIkkeStanseUtbetalinger.SendingAvUtebetalingTilOppdragFeilet ->
                                call.respond(InternalServerError, "Oversendelse til oppdrag feilet")
                            KunneIkkeStanseUtbetalinger.SimulertStansHarBeløpUlikt0 ->
                                call.respond(
                                    InternalServerError,
                                    "Simulering av stans inneholdt beløp for utbetaling ulikt 0"
                                )
                        }
                        InternalServerError.message("Kunne ikke stanse utbetalinger for sak med id $sakId")
                    },
                    {
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, null)
                        call.sikkerlogg("Stanser utbetaling på sak $sakId")
                        call.respond(serialize(it.toJson()))
                    }
                )
            }
        }
    }
}
