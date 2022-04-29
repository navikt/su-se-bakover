package no.nav.su.se.bakover.web.routes.regulering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.regulering.ReguleringService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import java.time.LocalDate

private data class Request(val startDato: LocalDate)

internal fun Route.regulerAutomatisk(
    reguleringService: ReguleringService,
) {
        post("$reguleringPath/automatisk") {
    authorize(Brukerrolle.Drift) {
            call.withBody<Request> {
                CoroutineScope(Dispatchers.IO).launch {
                    reguleringService.startRegulering(it.startDato)
                }
                call.svar(Resultat.okJson(HttpStatusCode.OK))
            }
        }
    }
}
