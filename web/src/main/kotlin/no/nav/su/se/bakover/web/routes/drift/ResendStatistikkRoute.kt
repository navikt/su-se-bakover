package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.trimWhitespace
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
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
                CoroutineScope(Dispatchers.IO).launch {
                    resendStatistikkhendelserService.resendIverksattSøknadsbehandling(LocalDate.parse(body.fraOgMed))
                }

                call.svar(Resultat.json(HttpStatusCode.Accepted, """{"status": "Accepted"}"""))
            }
        }
    }

    post("$DRIFT_PATH/resend-statistikk/vedtak") {
        data class Body(val vedtak: String)
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> {
                val ider = it.vedtak.trimWhitespace().split(",").map { UUID.fromString(it.trim()) }
                CoroutineScope(Dispatchers.IO).launch {
                    ider.forEach {
                        resendStatistikkhendelserService.resendStatistikkForVedtak(it)
                    }
                }
                call.svar(Resultat.okJson())
            }
        }
    }
}
