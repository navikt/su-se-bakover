package no.nav.su.se.bakover.web.routes.utbetaling.start

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingerService
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.withSakId

internal fun Route.startutbetalingRoutes(
    service: StartUtbetalingerService
) {
    post("$sakPath/{sakId}/utbetalinger/gjenoppta") {
        call.withSakId { sakId ->
            service.startUtbetalinger(sakId).fold(
                {
                    when (it) {
                        StartUtbetalingFeilet.FantIkkeSak -> call.respond(NotFound, "Fant ikke sak")
                        StartUtbetalingFeilet.HarIngenOversendteUtbetalinger -> call.respond(
                            BadRequest,
                            "Ingen utbetalinger"
                        )
                        StartUtbetalingFeilet.SisteUtbetalingErIkkeEnStansutbetaling -> call.respond(
                            BadRequest,
                            "Siste utbetaling er ikke en stans"
                        )
                        StartUtbetalingFeilet.SimuleringAvStartutbetalingFeilet -> call.respond(
                            InternalServerError,
                            "Simulering feilet"
                        )
                        StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet -> call.respond(
                            InternalServerError,
                            "Oversendelse til oppdrag feilet"
                        )
                    }
                },
                { call.respond(serialize(it.toJson())) }
            )
        }
    }
}
