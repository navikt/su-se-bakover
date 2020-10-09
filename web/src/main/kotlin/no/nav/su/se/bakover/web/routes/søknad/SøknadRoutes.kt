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
import no.nav.su.se.bakover.domain.TrukketSøknadBody
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withSøknadId
import org.slf4j.LoggerFactory

internal const val søknadPath = "/soknad"

internal fun Route.søknadRoutes(
    mediator: SøknadRouteMediator,
    søknadService: SøknadService
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
                call.svar(
                    Resultat.json(Created, serialize((mediator.nySøknad(it.toSøknadInnhold()).toJson())))
                )
            }
        )
    }

    post("$søknadPath/{søknadId}/trekkSøknad") {
        call.withSøknadId {
            Either.catch { deserialize<TrukketSøknadBody>(call) }.fold(
                ifLeft = {
                    log.info("Ugyldig søknads avslutting-body: ", it)
                    call.svar(HttpStatusCode.BadRequest.message("Ugyldig body"))
                },
                ifRight = { trukketSøknadBody ->
                    if (trukketSøknadBody.valid()) {
                        søknadService.trekkSøknad(trukketSøknadBody).fold(
                            ifLeft = {
                                call.svar(HttpStatusCode.InternalServerError.message("Noe gikk galt"))
                            },
                            ifRight = {
                                call.svar(
                                    HttpStatusCode.OK.message(
                                        "Trukket søknad for ${trukketSøknadBody.søknadId} "
                                    )
                                )
                            }
                        )
                    } else {
                        call.svar(HttpStatusCode.BadRequest.message("Ugyldige begrunnelse for trekking av søknad: $it"))
                    }
                }
            )
        }
    }
}
