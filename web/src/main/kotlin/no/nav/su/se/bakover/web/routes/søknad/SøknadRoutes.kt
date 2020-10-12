package no.nav.su.se.bakover.web.routes.søknad

import SuMetrics
import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.TrekkSøknadFeil
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withSakId
import no.nav.su.se.bakover.web.withSøknadId

internal const val søknadPath = "/soknad"

internal fun Route.søknadRoutes(
    mediator: SøknadRouteMediator,
    søknadService: SøknadService
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

    post("$søknadPath/{sakId}/{søknadId}/trekk") {
        call.withSakId {
            call.withSøknadId { søknadId ->
                Either.catch { deserialize<Saksbehandler>(call) }.fold(
                    ifLeft = {
                        call.svar(
                            HttpStatusCode.BadRequest.message(
                                "Ugyldig nav-ident"
                            )
                        )
                    },
                    ifRight = { saksbehandler ->
                        søknadService.trekkSøknad(søknadId = søknadId, saksbehandler = saksbehandler).fold(
                            ifLeft = {
                                when (it) {
                                    is TrekkSøknadFeil.KunneIkkeTrekkeSøknad ->
                                        call.svar(HttpStatusCode.InternalServerError.message("Noe gikk galt"))
                                    is TrekkSøknadFeil.SøknadErAlleredeTrukket ->
                                        call.svar(HttpStatusCode.BadRequest.message("Søknad er allerede trukket"))
                                    is TrekkSøknadFeil.SøknadHarEnBehandling ->
                                        call.svar(Created.message("Søknaden har en behandling"))
                                }
                            },
                            ifRight = {
                                call.svar(
                                    HttpStatusCode.OK.message(
                                        "Trukket søknad for $søknadId "
                                    )
                                )
                            }
                        )
                    }
                )
            }
        }
    }
}
