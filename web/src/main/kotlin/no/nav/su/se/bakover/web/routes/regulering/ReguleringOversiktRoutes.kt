package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.regulering.Reguleringsjobb
import no.nav.su.se.bakover.service.regulering.ReguleringService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withStringParam

internal fun Route.reguleringOversiktRoutes(
    reguleringService: ReguleringService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        get("$reguleringPath/status/{reguleringsjobb}") {
            call.withStringParam("reguleringsjobb") { jobbnavn ->
                Either.catch {
                    Reguleringsjobb.valueOf(jobbnavn)
                }.fold(
                    ifLeft = { call.svar(HttpStatusCode.BadRequest.errorJson("Ugyldig jobbnavn", "ugyldig_jobbnavn")) },
                    ifRight = { reguleringsjobb ->
                        val json = reguleringService.hentStatus(reguleringsjobb).map { it.toJson() }
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(json)))
                    }
                )
            }
        }
    }
}
