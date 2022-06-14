package no.nav.su.se.bakover.web.routes.avstemming

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.mapBoth
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.svar
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val AVSTEMMING_PATH = "/avstemming"

// TODO jah Jacob: Consider if this is still needed.
internal fun Route.avstemmingRoutes(
    service: AvstemmingService,
    clock: Clock,
) {
    post("$AVSTEMMING_PATH/grensesnitt") {
        authorize(Brukerrolle.Drift) {
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
                                tilOgMed,
                            ).mapBoth { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }
                        }
                            .mapLeft {
                                return@authorize call.svar(
                                    HttpStatusCode.BadRequest.errorJson(
                                        message = "Ugyldig(e) dato(er). Må være på ${DateTimeFormatter.ISO_DATE}",
                                        code = "ugyldig_datoformat",
                                    ),
                                )
                            }
                            .map {
                                if (!isValidAvstemmingsperiode(it, clock)) {
                                    return@authorize call.svar(
                                        HttpStatusCode.BadRequest.errorJson(
                                            message = "fraOgMed må være <= tilOgMed. Og tilOgMed må være tidligere enn dagens dato!",
                                            code = "ugyldig_dato",
                                        ),
                                    )
                                }
                                it
                            }
                    else ->
                        return@authorize call.svar(
                            HttpStatusCode.BadRequest.errorJson(
                                "Ugyldig as",
                                "",
                            ),
                        )
                }

            periode.fold(
                { service.grensesnittsavstemming() },
                {
                    service.grensesnittsavstemming(it.first.startOfDay(), it.second.endOfDay())
                },
            )
                .fold(
                    {
                        call.svar(
                            HttpStatusCode.InternalServerError.errorJson(
                                "Kunne ikke avstemme",
                                "kunne_ikke_avstemme",
                            ),
                        )
                    },
                    { call.respondText("Avstemt ok") },
                )
        }
    }

    post("$AVSTEMMING_PATH/konsistens") {
        authorize(Brukerrolle.Drift) {
            val fraOgMed = call.parameters["fraOgMed"]

            if (fraOgMed == null) call.svar(
                HttpStatusCode.BadRequest.errorJson(
                    "Parameter 'fraOgMed' mangler",
                    "ugyldig_parameter",
                ),
            ) else {
                service.konsistensavstemming(LocalDate.parse(fraOgMed, DateTimeFormatter.ISO_DATE))
                    .bimap(
                        {
                            call.svar(
                                HttpStatusCode.InternalServerError.errorJson(
                                    "Avstemming feilet",
                                    "avstemming_feilet",
                                ),
                            )
                        },
                        {
                            call.svar(
                                Resultat.json(
                                    httpCode = HttpStatusCode.OK,
                                    json = """{"message":"Konsistensavstemming fullført for tidspunkt:${it.løpendeFraOgMed} for utbetalinger opprettet tilOgMed:${it.opprettetTilOgMed}"}""",
                                ),
                            )
                        },
                    )
            }
        }
    }
}

private fun isValidAvstemmingsperiode(periode: Pair<LocalDate, LocalDate>, clock: Clock) =
    (periode.first <= periode.second) && periode.second < LocalDate.now(clock)

private fun erBeggeNullOrEmpty(s1: String?, s2: String?) = s1.isNullOrEmpty() && s2.isNullOrEmpty()
private fun erIngenNullOrEmpty(s1: String?, s2: String?) = !s1.isNullOrEmpty() && !s2.isNullOrEmpty()
