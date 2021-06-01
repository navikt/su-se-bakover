package no.nav.su.se.bakover.web.routes.avstemming

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.mapBoth
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.web.features.authorize
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val AVSTEMMING_PATH = "/avstem"

// TODO jah Jacob: Consider if this is still needed.
internal fun Route.avstemmingRoutes(
    service: AvstemmingService
) {
    authorize(Brukerrolle.Drift) {
        post(AVSTEMMING_PATH) {
            val fraOgMed = call.parameters["fraOgMed"] // YYYY-MM-DD
            val tilOgMed = call.parameters["tilOgMed"] // YYYY-MM-DD

            val periode: Either<Unit, Pair<LocalDate, LocalDate>> =
                when {
                    erBeggeNullOrEmpty(fraOgMed, tilOgMed) ->
                        Either.Left(Unit)
                    erIngenNullOrEmpty(fraOgMed, tilOgMed) ->
                        Either.catch {
                            Pair(
                                fraOgMed,
                                tilOgMed
                            ).mapBoth { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }
                        }
                            .mapLeft {
                                return@post call.respond(
                                    HttpStatusCode.BadRequest,
                                    "Ugyldig(e) dato(er). Må være på ${DateTimeFormatter.ISO_DATE}"
                                )
                            }
                            .map {
                                if (!isValidAvstemmingsperiode(it)) {
                                    return@post call.respond(
                                        HttpStatusCode.BadRequest,
                                        "fraOgMed må være <= tilOgMed. Og tilOgMed må være tidligere enn dagens dato!"
                                    )
                                }
                                it
                            }
                    else ->
                        return@post call.respond(HttpStatusCode.BadRequest, "Ugyldig as")
                }

            periode.fold(
                { service.avstemming() },
                {
                    service.avstemming(it.first.startOfDay(), it.second.endOfDay())
                }
            )
                .fold(
                    { call.respond(HttpStatusCode.InternalServerError, "Kunne ikke avstemme") },
                    { call.respond("Avstemt ok") }
                )
        }
    }
}

private fun isValidAvstemmingsperiode(periode: Pair<LocalDate, LocalDate>) =
    (periode.first <= periode.second) && periode.second < LocalDate.now()

private fun erBeggeNullOrEmpty(s1: String?, s2: String?) = s1.isNullOrEmpty() && s2.isNullOrEmpty()
private fun erIngenNullOrEmpty(s1: String?, s2: String?) = !s1.isNullOrEmpty() && !s2.isNullOrEmpty()
