package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.søknad.KunneIkkeLageSøknadPdf
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.receiveTextUTF8
import no.nav.su.se.bakover.web.routes.søknad.lukk.LukkSøknadErrorHandler
import no.nav.su.se.bakover.web.routes.søknad.lukk.LukkSøknadInputHandler
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withSøknadId

internal const val søknadPath = "/soknad"

@KtorExperimentalAPI
internal fun Route.søknadRoutes(
    søknadService: SøknadService,
    lukkSøknadService: LukkSøknadService,
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
                        { (saksnummer, søknad) ->
                            call.audit(søknad.søknadInnhold.personopplysninger.fnr, AuditLogEvent.Action.CREATE, null)
                            call.sikkerlogg("Lagrer søknad ${søknad.id} på sak ${søknad.sakId}")
                            call.svar(
                                Resultat.json(
                                    Created,
                                    serialize(
                                        OpprettetSøknadJson(
                                            saksnummer = saksnummer.nummer,
                                            søknad = søknad.toJson()
                                        )
                                    )
                                )
                            )
                        }
                    )
                }
            )
        }
    }

    authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
        get("$søknadPath/{søknadId}/utskrift") {
            call.withSøknadId { søknadId ->
                søknadService.hentSøknadPdf(søknadId).fold(
                    {
                        val responseMessage = when (it) {
                            KunneIkkeLageSøknadPdf.FantIkkeSøknad -> NotFound.message("Fant ikke søknad")
                            KunneIkkeLageSøknadPdf.KunneIkkeLagePdf -> InternalServerError.message("Kunne ikke lage PDF")
                            KunneIkkeLageSøknadPdf.FantIkkePerson -> NotFound.message("Fant ikke person")
                            KunneIkkeLageSøknadPdf.FantIkkeSak -> NotFound.message("Fant ikke sak")
                        }
                        call.respond(responseMessage)
                    },
                    {
                        call.respondBytes(it, ContentType.Application.Pdf)
                    }
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$søknadPath/{søknadId}/lukk") {
            call.withSøknadId { søknadId ->
                LukkSøknadInputHandler.handle(
                    body = call.receiveTextUTF8(),
                    søknadId = søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent)
                ).mapLeft {
                    call.svar(BadRequest.message("Ugyldig input"))
                }.map { request ->
                    lukkSøknadService.lukkSøknad(request).fold(
                        { call.svar(LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(request, it)) },
                        {
                            call.audit(it.sak.fnr, AuditLogEvent.Action.UPDATE, null)
                            call.sikkerlogg("Lukket søknad for søknad: $søknadId")
                            call.svar(LukkSøknadErrorHandler.lukketSøknadResponse(it))
                        }
                    )
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$søknadPath/{søknadId}/lukk/brevutkast") {
            call.withSøknadId { søknadId ->
                LukkSøknadInputHandler.handle(
                    body = call.receiveTextUTF8(),
                    søknadId = søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent)
                ).mapLeft {
                    call.svar(BadRequest.message("Ugyldig input"))
                }.map { request ->
                    lukkSøknadService.lagBrevutkast(
                        request
                    ).fold(
                        {
                            when (it) {
                                KunneIkkeLageBrevutkast.FantIkkeSøknad ->
                                    call.svar(NotFound.message("Fant Ikke Søknad"))
                                KunneIkkeLageBrevutkast.UkjentBrevtype ->
                                    call.svar(BadRequest.message("Kunne ikke lage brev for ukjent brevtype"))
                                else ->
                                    call.svar(InternalServerError.message("Kunne ikke lage brevutkast"))
                            }
                        },
                        { call.respondBytes(it, ContentType.Application.Pdf) }
                    )
                }
            }
        }
    }
}
