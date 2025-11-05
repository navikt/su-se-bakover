package økonomi.presentation.api

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import økonomi.application.utbetaling.ResendUtbetalingService

internal const val ØKONOMI_PATH = "/okonomi"

fun Route.økonomiRoutes(
    resendUtbetalingService: ResendUtbetalingService,
) {
    post("$ØKONOMI_PATH/utbetalingslinjer") {
        authorize(Brukerrolle.Drift) {
            data class UtbetalingslinjerBody(val utbetalingslinjer: String) {
                fun toUtbetalingslinjer(): List<UUID30> = utbetalingslinjer
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    // TODO jah: Denne kan kaste
                    .map { UUID30.fromString(it) }
            }

            data class ResponseError(val utbetalingId: String, val feilmelding: String)
            data class ResponseSuccess(val utbetalingId: String)
            data class Response(val success: List<ResponseSuccess>, val failed: List<ResponseError>)

            call.withBody<UtbetalingslinjerBody> { body ->
                val response = resendUtbetalingService.resendUtbetalinger(body.toUtbetalingslinjer()).map {
                    it.map {
                        ResponseSuccess(it.toString())
                    }.mapLeft {
                        ResponseError(it.utbetalingId.toString(), it.feilMelding())
                    }
                }.let {
                    Response(
                        success = it.filterIsInstance<Either.Right<ResponseSuccess>>().map { it.value },
                        failed = it.filterIsInstance<Either.Left<ResponseError>>().map { it.value },
                    )
                }
                call.svar(
                    Resultat.json(
                        httpCode = HttpStatusCode.OK,
                        json = serialize(response),
                    ),
                )
            }
        }
    }
}
