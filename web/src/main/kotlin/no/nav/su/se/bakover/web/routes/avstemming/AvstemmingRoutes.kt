package no.nav.su.se.bakover.web.routes.avstemming

import arrow.core.Either
import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.mapBoth
import no.nav.su.se.bakover.common.domain.tid.endOfDay
import no.nav.su.se.bakover.common.domain.tid.startOfDay
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import økonomi.domain.Fagområde
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val AVSTEMMING_PATH = "/avstemming"

private fun String.toFagområde(): Fagområde {
    return when {
        this == "SUALDER" -> Fagområde.SUALDER
        this == "SUUFORE" -> Fagområde.SUUFORE
        else -> throw IllegalArgumentException("Ukjent fagområde: $this")
    }
}

// TODO jah Jacob: Consider if this is still needed.
internal fun Route.avstemmingRoutes(
    service: AvstemmingService,
    clock: Clock,
) {
    post("$AVSTEMMING_PATH/grensesnitt") {
        authorize(Brukerrolle.Drift) {
            val fraOgMed = call.parameters["fraOgMed"] // YYYY-MM-DD
            val tilOgMed = call.parameters["tilOgMed"] // YYYY-MM-DD
            val fagområdeString = call.parameters["fagomrade"]

            val periode: Either<Unit, Pair<LocalDate, LocalDate>> = when {
                erBeggeNullOrEmpty(fraOgMed, tilOgMed) -> Either.Left(Unit)

                erIngenNullOrEmpty(fraOgMed, tilOgMed) -> Either.catch {
                    Pair(
                        fraOgMed,
                        tilOgMed,
                    ).mapBoth { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }
                }.mapLeft {
                    return@authorize call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            message = "Ugyldig(e) dato(er). Må være på ${DateTimeFormatter.ISO_DATE}",
                            code = "ugyldig_datoformat",
                        ),
                    )
                }.map {
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

                else -> return@authorize call.svar(
                    HttpStatusCode.BadRequest.errorJson(
                        "Ugyldige parametere",
                        "",
                    ),
                )
            }

            val fagområde = Either.catch {
                fagområdeString?.toFagområde()
            }.getOrElse {
                return@authorize call.svar(
                    HttpStatusCode.BadRequest.errorJson(
                        message = "${it.message}",
                        code = "ugyldig_input",
                    ),
                )
            }!!

            periode.fold(
                { service.grensesnittsavstemming(fagområde) },
                {
                    service.grensesnittsavstemming(it.first.startOfDay(), it.second.endOfDay(), fagområde)
                },
            ).fold(
                {
                    call.svar(
                        HttpStatusCode.InternalServerError.errorJson(
                            message = "Kunne ikke avstemme",
                            code = "kunne_ikke_avstemme",
                        ),
                    )
                },
                {
                    call.svar(
                        Resultat.json(
                            httpCode = HttpStatusCode.Created,
                            json = """{"message":"Grensesnittsavstemming fullført for fagområde:$fagområde, periode:${it.fraOgMed}-${it.tilOgMed}, antall:${it.utbetalinger.count()}"}""",
                        ),
                    )
                },
            )
        }
    }

    post("$AVSTEMMING_PATH/konsistens") {
        authorize(Brukerrolle.Drift) {
            val fraOgMed = call.parameters["fraOgMed"]
            val fagområdeString = call.parameters["fagomrade"]

            if (fraOgMed == null) {
                call.svar(
                    HttpStatusCode.BadRequest.errorJson(
                        "Parameter 'fraOgMed' mangler",
                        "ugyldig_parameter",
                    ),
                )
            }

            val fagområde = Either.catch {
                fagområdeString?.toFagområde()
            }.getOrElse {
                return@authorize call.svar(
                    HttpStatusCode.BadRequest.errorJson(
                        message = "${it.message}",
                        code = "ugyldig_input",
                    ),
                )
            }!!

            service.konsistensavstemming(LocalDate.parse(fraOgMed, DateTimeFormatter.ISO_DATE), fagområde).map {
                call.svar(
                    Resultat.json(
                        httpCode = HttpStatusCode.OK,
                        json = """{"message":"Konsistensavstemming fullført for fagområde:$fagområde, tidspunkt:${it.løpendeFraOgMed} for utbetalinger opprettet tilOgMed:${it.opprettetTilOgMed}"}""",
                    ),
                )
            }.mapLeft {
                call.svar(
                    HttpStatusCode.InternalServerError.errorJson(
                        message = "Avstemming feilet",
                        code = "avstemming_feilet",
                    ),
                )
            }
        }
    }
}

private fun isValidAvstemmingsperiode(periode: Pair<LocalDate, LocalDate>, clock: Clock) =
    (periode.first <= periode.second) && periode.second < LocalDate.now(clock)

private fun erBeggeNullOrEmpty(s1: String?, s2: String?) = s1.isNullOrEmpty() && s2.isNullOrEmpty()
private fun erIngenNullOrEmpty(s1: String?, s2: String?) = !s1.isNullOrEmpty() && !s2.isNullOrEmpty()
