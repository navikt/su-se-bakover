package no.nav.su.se.bakover.web.routes.behandlinger.stopp

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandlinger.stopp.StoppbehandlingService
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.sak.withSak
import no.nav.su.se.bakover.web.svar

internal fun Route.stoppbehandlingRoutes(
    stoppbehandlingService: StoppbehandlingService,
    sakRepo: ObjectRepo
) {

    data class StoppUtbetalingerBody(val stoppÅrsak: String)

    post("$sakPath/{sakId}/utbetalinger/stopp") {
        call.withSak(sakRepo) { sak ->
            Either.catch { deserialize<StoppUtbetalingerBody>(call) }
                .fold(
                    {
                        call.svar(HttpStatusCode.BadRequest.message("Ugyldig body"))
                    },
                    {
                        call.svar(
                            stoppbehandlingService.stoppUtbetalinger(
                                sak = sak,
                                // TODO: Hent saksbehandler vha. JWT
                                saksbehandler = Saksbehandler(id = "saksbehandler"),
                                stoppÅrsak = it.stoppÅrsak
                            ).fold(
                                {
                                    InternalServerError.message("Kunne ikke opprette stoppbehandling for sak med id ${sak.id}")
                                },
                                {
                                    it.toResultat(OK)
                                }
                            )
                        )
                    }
                )
        }
    }
}
