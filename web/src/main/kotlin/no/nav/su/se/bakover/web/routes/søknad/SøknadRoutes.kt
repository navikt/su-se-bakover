package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.service.søknad.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.søknad.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteSøknad
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
import java.time.LocalDate

internal const val søknadPath = "/soknad"

data class TrekkSøknadJson(
    val datoSøkerTrakkSøknad: LocalDate
)

@KtorExperimentalAPI
internal fun Route.søknadRoutes(
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
                    søknadService.nySøknad(it.toSøknadInnhold()).fold(
                        { kunneIkkeOppretteSøknad ->
                            call.svar(
                                when (kunneIkkeOppretteSøknad) {
                                    KunneIkkeOppretteSøknad.FantIkkePerson -> NotFound.message("Fant ikke person")
                                }
                            )
                        },
                        { sak ->
                            call.audit("Lagrer søknad for person: $sak")
                            call.svar(
                                Resultat.json(Created, serialize((sak.toJson())))
                            )
                        }
                    )
                }
            )
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$søknadPath/{søknadId}/trekk") {
            call.withSøknadId { søknadId ->
                Either.catch { deserialize<TrekkSøknadJson>(call) }.fold(
                    {
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    { trekkSøknadJson ->
                        søknadService.trekkSøknad(
                            søknadId = søknadId,
                            trukketDato = trekkSøknadJson.datoSøkerTrakkSøknad,
                            saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent())
                        ).fold(
                            ifLeft = {
                                when (it) {
                                    is KunneIkkeLukkeSøknad.SøknadErAlleredeLukket ->
                                        call.svar(BadRequest.message("Søknad er allerede trukket"))
                                    is KunneIkkeLukkeSøknad.SøknadHarEnBehandling ->
                                        call.svar(BadRequest.message("Søknaden har en behandling"))
                                    is KunneIkkeLukkeSøknad.FantIkkeSøknad ->
                                        call.svar(NotFound.message("Fant ikke søknad for $søknadId"))
                                    KunneIkkeLukkeSøknad.KunneIkkeJournalføreBrev ->
                                        call.svar(InternalServerError.message("Kunne ikke journalføre brev"))
                                    KunneIkkeLukkeSøknad.KunneIkkeDistribuereBrev ->
                                        call.svar(InternalServerError.message("Kunne distribuere brev"))
                                }
                            },
                            ifRight = {
                                call.audit("Lukket søknad for søknad: $søknadId")
                                call.svar(Resultat.json(HttpStatusCode.OK, serialize((it.toJson()))))
                            }
                        )
                    }
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$søknadPath/{søknadId}/lukk/brevutkast") {
            call.withSøknadId { søknadId ->
                Either.catch { deserialize<TrekkSøknadJson>(call) }.fold(
                    {
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    { trekkSøknadJson ->
                        søknadService.lagBrevutkastForTrukketSøknad(
                            søknadId = søknadId,
                            trukketDato = trekkSøknadJson.datoSøkerTrakkSøknad
                        ).fold(
                            {
                                when (it) {
                                    KunneIkkeLageBrevutkast.FantIkkeSøknad ->
                                        call.svar(NotFound.message("Fant Ikke Søknad"))
                                    KunneIkkeLageBrevutkast.KunneIkkeLageBrev ->
                                        call.svar(InternalServerError.message("Kunne ikke lage brevutkast"))
                                }
                            },
                            { call.respondBytes(it, ContentType.Application.Pdf) }
                        )
                    }
                )
            }
        }
    }
}
