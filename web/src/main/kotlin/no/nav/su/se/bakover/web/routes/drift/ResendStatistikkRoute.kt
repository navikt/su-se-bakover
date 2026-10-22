package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.trimWhitespace
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.service.statistikk.ResendStatistikkhendelserService
import java.time.LocalDate
import java.util.UUID

internal fun Route.resendStatistikkRoutes(
    resendStatistikkhendelserService: ResendStatistikkhendelserService,
) {
    data class Body(
        val fraOgMed: String,
    )

    post("$DRIFT_PATH/resend-statistikk/vedtak/søknadsbehandling") {
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> { body ->
                val fraOgMed = runCatching { LocalDate.parse(body.fraOgMed) }.getOrElse {
                    return@withBody call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            "Ugyldig fraOgMed. Forventet datoformat er yyyy-MM-dd",
                            "ugyldig_datoformat",
                        ),
                    )
                }
                CoroutineScope(Dispatchers.IO).launch {
                    resendStatistikkhendelserService.resendIverksattSøknadsbehandling(fraOgMed)
                }

                call.svar(Resultat.json(HttpStatusCode.Accepted, """{"status": "Accepted"}"""))
            }
        }
    }

    post("$DRIFT_PATH/resend-statistikk/vedtak") {
        data class Body(val vedtak: String)
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> {
                val ider = runCatching {
                    it.vedtak.trimWhitespace().split(",").map { id -> UUID.fromString(id.trim()) }
                }.getOrElse {
                    return@withBody call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            "Vedtak må være en komma-separert liste med gyldige identifikatorer",
                            "ugyldig_vedtak_id",
                        ),
                    )
                }
                CoroutineScope(Dispatchers.IO).launch {
                    ider.forEach {
                        resendStatistikkhendelserService.resendStatistikkForVedtak(it)
                    }
                }
                call.svar(Resultat.okJson())
            }
        }
    }

    post("$DRIFT_PATH/resend-statistikk/sak") {
        data class Body(
            val fraOgMed: LocalDate,
            val tilOgMed: LocalDate,
        )
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> {
                CoroutineScope(Dispatchers.IO).launch {
                    resendStatistikkhendelserService.resendStatistikkForSak(fraOgMed = it.fraOgMed, it.tilOgMed)
                }
                call.svar(Resultat.okJson())
            }
        }
    }
}
