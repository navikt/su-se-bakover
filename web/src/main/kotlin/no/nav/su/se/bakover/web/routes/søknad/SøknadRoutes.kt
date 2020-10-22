package no.nav.su.se.bakover.web.routes.søknad

import SuMetrics
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
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.søknad.LukketSøknadJson
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
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

data class LukketSøknadBody(
    val typeLukking: LukketSøknadJson.TypeLukking,
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
                                    KunneIkkeOppretteSøknad.FantIkkePerson ->
                                        NotFound.message("Fant ikke person")
                                    KunneIkkeOppretteSøknad.KunneIkkeJournalføreSøknad ->
                                        InternalServerError.message("Kunne ikke journalføre søknad")
                                }
                            )
                        },
                        { sak ->
                            SuMetrics.Counter.Søknad.increment()
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
        post("$søknadPath/{søknadId}/lukk") {
            call.withSøknadId { søknadId ->
                Either.catch { deserialize<LukketSøknadBody>(call) }.fold(
                    ifLeft = {
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { lukketSøknadBody ->
                        val lukketSøknad = when (lukketSøknadBody.typeLukking) {
                            LukketSøknadJson.TypeLukking.Trukket -> Søknad.Lukket.Trukket(
                                tidspunkt = Tidspunkt.now(),
                                saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.getNAVIdent()),
                                datoSøkerTrakkSøknad = lukketSøknadBody.datoSøkerTrakkSøknad
                            )
                        }

                        søknadService.lukkSøknad(
                            søknadId = søknadId,
                            lukketSøknad = lukketSøknad
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
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$søknadPath/{søknadId}/lukk/brevutkast") {
            call.withSøknadId { søknadId ->
                Either.catch { deserialize<LukketSøknadBody>(call) }.fold(
                    ifLeft = {
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { lukketSøknadBody ->
                        val lukketSøknad = when (lukketSøknadBody.typeLukking) {
                            LukketSøknadJson.TypeLukking.Trukket -> Søknad.Lukket.Trukket(
                                tidspunkt = Tidspunkt.now(),
                                saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.getNAVIdent()),
                                datoSøkerTrakkSøknad = lukketSøknadBody.datoSøkerTrakkSøknad
                            )
                        }
                        søknadService.lagLukketSøknadBrevutkast(
                            søknadId = søknadId,
                            lukketSøknad = lukketSøknad
                        ).fold(
                            ifLeft = {
                                when (it) {
                                    is KunneIkkeLageBrevutkast.FantIkkeSøknad ->
                                        call.svar(BadRequest.message("Fant ikke søknad $søknadId"))
                                    KunneIkkeLageBrevutkast.FeilVedHentingAvPerson ->
                                        call.svar(BadRequest.message("Feil ved henting av person"))
                                    KunneIkkeLageBrevutkast.FeilVedGenereringAvBrevutkast ->
                                        call.svar(BadRequest.message("Feil ved generering av PDF"))
                                }
                            },
                            ifRight = {
                                call.respondBytes(it, ContentType.Application.Pdf)
                            }
                        )
                    }
                )
            }
        }
    }
}
