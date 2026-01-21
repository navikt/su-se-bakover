package no.nav.su.se.bakover.web.routes.drift

import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.service.statistikk.SakStatistikkBigQueryService
import java.time.LocalDate

internal fun Route.sakStatistikkRoutes(
    service: SakStatistikkBigQueryService,
) {
    post("$DRIFT_PATH/statistikk/sak") {
        data class Body(
            val fraOgMed: LocalDate,
            val tilOgMed: LocalDate,
        )
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> {
                withContext(Dispatchers.IO) {
                    service.lastTilBigQuery(it.fraOgMed, it.tilOgMed)
                }
                call.svar(Resultat.okJson())
            }
        }
    }
}
