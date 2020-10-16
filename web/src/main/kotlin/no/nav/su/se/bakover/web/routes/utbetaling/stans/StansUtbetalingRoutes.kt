package no.nav.su.se.bakover.web.routes.utbetaling.stans

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.utbetaling.StansUtbetalingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withSakId

internal fun Route.stansutbetalingRoutes(
    stansUtbetalingService: StansUtbetalingService
) {
    post("$sakPath/{sakId}/utbetalinger/stans") {
        call.withSakId { sakId ->
            call.svar(
                stansUtbetalingService.stansUtbetalinger(
                    sakId = sakId,
                    saksbehandler = call.suUserContext.getNAVIdent().let { NavIdentBruker.Saksbehandler(it) }
                ).fold(
                    {
                        InternalServerError.message("Kunne ikke stanse utbetalinger for sak med id $sakId")
                    },
                    {
                        Resultat.json(OK, serialize(it.toJson()))
                    }
                )
            )
        }
    }
}
