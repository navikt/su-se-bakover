package no.nav.su.se.bakover.web.routes.søknad

import SuMetrics
import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.service.søknad.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.søknad.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withSøknadId

internal const val søknadPath = "/soknad"

@KtorExperimentalAPI
internal fun Route.søknadRoutes(
    mediator: SøknadRouteMediator,
    søknadService: SøknadService
) {
    authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
        post(søknadPath) {
            Either.catch { deserialize<SøknadInnholdJson>(call) }.fold(
                ifLeft = {
                    call.application.environment.log.info(it.message, it)
                    call.svar(BadRequest.message("Ugyldig body"))
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

    authorize(Brukerrolle.Saksbehandler) {
        post("$søknadPath/{søknadId}/lukk") {
            call.withSøknadId { søknadId ->
                søknadService.lukkSøknad(
                    søknadId = søknadId,
                    saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent())
                ).fold(
                    ifLeft = {
                        when (it) {
                            is KunneIkkeLukkeSøknad.SøknadErAlleredeLukket ->
                                call.svar(BadRequest.message("Søknad er allerede trukket"))
                            is KunneIkkeLukkeSøknad.SøknadHarEnBehandling ->
                                call.svar(BadRequest.message("Søknaden har en behandling"))
                            is KunneIkkeLukkeSøknad.FantIkkeSøknad ->
                                call.svar(BadRequest.message("Fant ikke søknad for $søknadId"))
                            is KunneIkkeLukkeSøknad.KunneIkkeSendeBrev ->
                                call.svar(InternalServerError.message("Kunne ikke sende brev for $søknadId"))
                        }
                    },
                    ifRight = {
                        call.audit("Lukket søknad for søknad: $søknadId")
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize((it.toJson()))))
                    }
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        get("$søknadPath/{søknadId}/lukk/brevutkast") {
            val typeLukking = call.parameters["type"]?.let {
                Either.catch { Søknad.TypeLukking.valueOf(it) }.mapLeft { "Type er ikke en gyldig type lukking" }
            } ?: Either.Left("Type er ikke et parameter")

            typeLukking.fold(
                ifLeft = {
                    call.svar(BadRequest.message(it))
                },
                ifRight = {
                    call.withSøknadId { søknadId ->
                        søknadService.lagLukketSøknadBrevutkast(
                            søknadId = søknadId,
                            typeLukking = it
                        ).fold(
                            ifLeft = {
                                when (it) {
                                    is KunneIkkeLageBrevutkast.FantIkkeSøknad ->
                                        call.svar(BadRequest.message("Fant ikke søknad $søknadId"))
                                    KunneIkkeLageBrevutkast.FeilVedHentingAvPerson ->
                                        call.svar(BadRequest.message("Feil ved henting av person"))
                                }
                            },
                            ifRight = {
                                call.respondBytes(it, ContentType.Application.Pdf)
                            }
                        )
                    }
                }
            )
        }
    }
}
