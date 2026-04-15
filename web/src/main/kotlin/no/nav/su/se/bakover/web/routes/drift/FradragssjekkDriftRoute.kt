package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigMåned
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.web.services.fradragssjekken.FradragsSjekkFeil
import no.nav.su.se.bakover.web.services.fradragssjekken.FradragsjobbenService
import org.slf4j.LoggerFactory

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

                val result = fradragsjobbenService.validerKjøringForMåned(måned = måned)
                when (result) {
                    FradragsSjekkFeil.AlleredeKjørtForMåned -> return@withBody call.svar(
                        HttpStatusCode.Conflict.errorJson(
                            message = "Fradragssjekk er allerede kjørt for måned $måned",
                            code = "fradragssjekk_allerede_kjort_for_maaned",
                        ),
                    )
                    FradragsSjekkFeil.DatoErFremITid -> return@withBody call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            message = "Fradragssjekk kan ikke kjøres for fremtidig måned $måned",
                            code = "fradragssjekk_fremtidig_maaned_ikke_tillatt",
                        ),
                    )
                    FradragsSjekkFeil.DatoErTilbakeITid -> return@withBody call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            message = "Fradragssjekk kan ikke kjøres for tidligere måned $måned",
                            code = "fradragssjekk_tidligere_maaned_ikke_tillatt",
                        ),
                    )
                    null -> Unit
                }

                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        fradragsjobbenService.kjørFradragssjekkForMåned(
                            måned = måned,
                            dryRun = body.dryRun,
                        )
                    }.onFailure {
                        log.error("Manuell fradragssjekk feilet for måned {}. dryRun={}", måned, body.dryRun, it)
                    }
                }

                call.svar(Resultat.accepted())
            }
        }
    }
}
