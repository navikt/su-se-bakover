package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.routing.Route
import io.ktor.routing.post

import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.jsonBody
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
                call.audit("Lagrer søknad for person: $it")
                call.svar(Created.jsonBody(mediator.nySøknad(it.toSøknadInnhold())))
            }
        )
    }
}
