package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.service.statistikk.StønadStatistikkJobService
import java.time.LocalDate
import java.time.YearMonth

internal fun Route.stønadstatistikkRoutes(
    service: StønadStatistikkJobService,
) {
    data class Body(
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
    ) {
        fun erGyldig() = fraOgMed.isAfter(YearMonth.of(2019, 12).atEndOfMonth()) &&
            tilOgMed.isBefore(YearMonth.now().atDay(1))
    }

    val feilrespons = BadRequest.errorJson(
        "Periode må være mellom januar 2020 til og med forrige måned.",
        "ugyldig_dato",
    )

    post("$DRIFT_PATH/statistikk/stønad/lag") {
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> { body ->
                if (!body.erGyldig()) {
                    call.svar(feilrespons)
                } else {
                    service.lagStatistikkForFlereMåneder(
                        YearMonth.from(body.fraOgMed),
                        YearMonth.from(body.tilOgMed),
                    )
                    call.svar(Resultat.okJson())
                }
            }
        }
    }
    post("$DRIFT_PATH/statistikk/stønad/send") {
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> { body ->
                if (!body.erGyldig()) {
                    call.svar(feilrespons)
                } else {
                    service.sendMånederTilBQ(
                        YearMonth.from(body.fraOgMed),
                        YearMonth.from(body.tilOgMed),
                    )
                    call.svar(Resultat.okJson())
                }
            }
        }
    }
}
