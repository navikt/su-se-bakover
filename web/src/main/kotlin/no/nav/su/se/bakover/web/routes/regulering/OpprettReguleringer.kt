package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.getOrHandle
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

internal fun Route.oppdaterReguleringer(
    reguleringService: ReguleringService,
) {
    authorize(Brukerrolle.Drift) {
        post("$reguleringPath/automatisk") {
            val dato = call.request.queryParameters["dato"].let {
                if (it.isNullOrEmpty()) null else it.formaterDato().getOrHandle {
                    call.svar(it)
                    return@post
                }
            }
            call.svar(Resultat.json(HttpStatusCode.OK, serialize(reguleringService.kjørAutomatiskRegulering(dato))))
        }
    }
}
