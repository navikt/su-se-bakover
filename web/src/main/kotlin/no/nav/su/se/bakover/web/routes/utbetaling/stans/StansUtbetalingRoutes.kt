package no.nav.su.se.bakover.web.routes.utbetaling.stans

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.StansUtbetalingService
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson.Companion.jsonBody
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.sak.withSak
import no.nav.su.se.bakover.web.svar

internal fun Route.stansutbetalingRoutes(
    stansUtbetalingService: StansUtbetalingService,
    sakService: SakService
) {
    post("$sakPath/{sakId}/utbetalinger/stans") {
        call.withSak(sakService) { sak ->
            call.svar(
                stansUtbetalingService.stansUtbetalinger(
                    sak = sak,
                    // TODO: Hent saksbehandler vha. JWT
                ).fold(
                    {
                        InternalServerError.message("Kunne ikke stanse utbetalinger for sak med id ${sak.id}")
                    },
                    {
                        OK.jsonBody(it)
                    }
                )
            )
        }
    }
}
