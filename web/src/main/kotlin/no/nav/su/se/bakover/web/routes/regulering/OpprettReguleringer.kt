package no.nav.su.se.bakover.web.routes.regulering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.regulering.ReguleringService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import java.time.LocalDate

private data class Request(val startDato: LocalDate)

internal fun Route.oppdaterReguleringer(
    reguleringService: ReguleringService,
) {
    authorize(Brukerrolle.Drift) {
        post("$reguleringPath/automatisk") {
            call.withBody<Request> {
                call.svar(
                    Resultat.json(HttpStatusCode.OK, serialize(reguleringService.startRegulering(it.startDato))),
                )
            }
        }
    }
}
