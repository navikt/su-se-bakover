package no.nav.su.se.bakover.web.routes.drift

import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
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
    )
    post("$DRIFT_PATH/statistikk/stønad/lag") {
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> {
                service.lagStatistikkForFlereMåneder(
                    YearMonth.from(it.fraOgMed),
                    YearMonth.from(it.tilOgMed),
                )
                call.svar(Resultat.okJson())
            }
        }
    }
    post("$DRIFT_PATH/statistikk/stønad/send") {
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> {
                service.sendMånederTilBQ(
                    YearMonth.from(it.fraOgMed),
                    YearMonth.from(it.tilOgMed),
                )
                call.svar(Resultat.okJson())
            }
        }
    }
}
