package no.nav.su.se.bakover.web.routes.søknad

import SuMetrics
import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.domain.AvsluttSøknadsBehandlingBody
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.jsonBody
import no.nav.su.se.bakover.web.svar
import org.slf4j.LoggerFactory

internal const val søknadPath = "/soknad"

internal fun Route.søknadRoutes(
    mediator: SøknadRouteMediator
) {

    val log = LoggerFactory.getLogger(this::class.java)

    post(søknadPath) {
        Either.catch { deserialize<SøknadInnholdJson>(call) }.fold(
            ifLeft = {
                call.application.environment.log.info(it.message, it)
                call.svar(HttpStatusCode.BadRequest.message("Ugyldig body"))
            },
            ifRight = {
                SuMetrics.Counter.Søknad.increment()
                call.audit("Lagrer søknad for person: $it")
                call.svar(Created.jsonBody(mediator.nySøknad(it.toSøknadInnhold())))
            }
        )
    }

    post("$søknadPath/{søknadId}/avsluttSoknadsbehandling") {
        Either.catch { deserialize<AvsluttSøknadsBehandlingBody>(call) }.fold(
            ifLeft = {
                log.info("Ugyldig behandling-body: ", it)
                call.svar(HttpStatusCode.BadRequest.message("Ugyldig body"))
            },
            ifRight = {
                if (it.valid()) {
                    mediator.avsluttSøknadsBehandling(it.søknadId, it.avsluttSøkndsBehandlingBegrunnelse)
                    call.svar(HttpStatusCode.OK.message("Avsluttet behandling for ${it.søknadId}"))
                } else {
                    call.svar(HttpStatusCode.BadRequest.message("Ugyldige begrunnelse for sletting: $it"))
                }
            }
        )

    }

}
