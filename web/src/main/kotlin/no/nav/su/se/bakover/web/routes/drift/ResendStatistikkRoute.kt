package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.service.statistikk.ResendStatistikkhendelserService
import no.nav.su.se.bakover.web.features.authorize
import java.time.LocalDate

internal fun Route.resendStatistikkRoute(
    resendStatistikkhendelserService: ResendStatistikkhendelserService,
) {
    data class Body(
        val fraOgMed: String,
    )

    post("/drift/resend-statistikk/vedtak/søknadsbehandling") {
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> { body ->
                CoroutineScope(Dispatchers.IO).launch {
                    resendStatistikkhendelserService.resendIverksattSøknadsbehandling(LocalDate.parse(body.fraOgMed))
                }

                call.svar(Resultat.json(HttpStatusCode.Accepted, """{"status": "Accepted"}"""))
            }
        }
    }
}
