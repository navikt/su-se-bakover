package no.nav.su.se.bakover.web.routes.utbetaling.start

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.withSakId

internal fun Route.startutbetalingRoutes(
    service: UtbetalingService
) {
    post("$sakPath/{sakId}/utbetalinger/start") {
        call.withSakId { sakId ->
            service.startUtbetalinger(sakId).fold(
                { call.respond(InternalServerError, "Kunne ikke starte utbetaling") },
                { call.respond("Startet ok") }
            )
        }
    }
}
