package no.nav.su.se.bakover.web.external

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.infrastructure.PeriodeMedOptionalTilOgMedJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.SakService

/**
 * Laget slik at uføre kan sende inn et fnr og tilhørende vedtaksperiode. Så svarer vi om fnr har supplerende stønad i den perioden.
 */
internal fun Route.pensjonRoutes(
    sakService: SakService,
) {
    data class Body(
        val fnr: String,
        val vedtaksperiode: PeriodeMedOptionalTilOgMedJson,
    ) {
        fun fnrDomain() = Fnr(fnr)
        fun periodeDomain() = vedtaksperiode.toDomain()
    }
    post("/pensjon/harSupplerendeStønad") {
        this.call.withBody<Body> { body ->
            if (sakService.harFnrStønadForPeriode(
                    fnr = body.fnrDomain(),
                    periode = body.periodeDomain(),
                )
            ) {
                call.svar(Resultat.json(HttpStatusCode.OK, """{"harSupplerendeStønad": true}"""))
            } else {
                call.svar(Resultat.json(HttpStatusCode.NotFound, """{"harSupplerendeStønad": false}"""))
            }
        }
    }
}
