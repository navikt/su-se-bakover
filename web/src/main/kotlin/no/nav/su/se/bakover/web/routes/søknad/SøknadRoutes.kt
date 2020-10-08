package no.nav.su.se.bakover.web.routes.søknad

import SuMetrics
import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.svar

internal const val søknadPath = "/soknad"

internal fun Route.søknadRoutes(
    mediator: SøknadRouteMediator
) {
    post(søknadPath) {
        Either.catch { deserialize<SøknadInnholdJson>(call) }.fold(
            ifLeft = {
                call.application.environment.log.info(it.message, it)
                call.svar(HttpStatusCode.BadRequest.message("Ugyldig body"))
            },
            ifRight = {
                SuMetrics.Counter.Søknad.increment()
                call.audit("Lagrer søknad for person: $it")
                call.svar(
                    Resultat.json(Created, serialize((mediator.nySøknad(it.toSøknadInnhold()).toJson())))
                )
            }
        )
    }
}
