package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.config.isGCP
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import java.time.LocalDate

// TODO: mulig vi skal slå sammen alle jobber til denne routen
internal fun Route.sakStatistikkRoutes(
    service: SakStatistikkService,
) {
    post("$DRIFT_PATH/statistikk/sak") {
        data class Body(
            val fraOgMed: LocalDate,
        )
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> {
                if (!isGCP()) {
                    call.svar(
                        BadRequest.errorJson(
                            "Manuell last av statistikk til BigQuery kun tillatt i GCP",
                            "STATISTIKK_BIGQUERY_KUN_GCP",
                        ),
                    )
                    return@withBody
                }
                CoroutineScope(Dispatchers.IO).launch {
                    service.lastTilBigQuery(it.fraOgMed)
                }
                call.svar(Resultat.okJson())
            }
        }
    }
}
