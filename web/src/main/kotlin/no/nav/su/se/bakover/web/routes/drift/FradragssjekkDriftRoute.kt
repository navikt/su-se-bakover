package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.correlation.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigMåned
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.web.services.fradragssjekken.FradragsjobbenService
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal fun Route.fradragssjekkDriftRoute(
    fradragsjobbenService: FradragsjobbenService,
) {
    data class Body(
        val maaned: String,
        val dryRun: Boolean = false,
    )

    val log = LoggerFactory.getLogger("FradragssjekkDriftRoute")

    post("$DRIFT_PATH/fradragssjekk/kjor") {
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> { body ->
                val måned = Måned.parse(body.maaned) ?: return@withBody call.svar(ugyldigMåned)
                val correlationId = call.correlationId.toString()

                if (!body.dryRun && fradragsjobbenService.harOrdinaerKjoringForMåned(måned)) {
                    return@withBody call.svar(
                        HttpStatusCode.Conflict.errorJson(
                            message = "Fradragssjekk er allerede kjørt for måned $måned",
                            code = "fradragssjekk_allerede_kjort_for_maaned",
                        ),
                    )
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val previousCorrelationId = MDC.get(CORRELATION_ID_HEADER)

                    try {
                        MDC.put(CORRELATION_ID_HEADER, correlationId)

                        runCatching {
                            fradragsjobbenService.kjørFradragssjekkForMåned(
                                måned = måned,
                                dryRun = body.dryRun,
                            )
                        }.onFailure {
                            log.error("Manuell fradragssjekk feilet for måned {}. dryRun={}", måned, body.dryRun, it)
                        }
                    } finally {
                        if (previousCorrelationId == null) {
                            MDC.remove(CORRELATION_ID_HEADER)
                        } else {
                            MDC.put(CORRELATION_ID_HEADER, previousCorrelationId)
                        }
                    }
                }

                call.svar(Resultat.accepted())
            }
        }
    }
}
