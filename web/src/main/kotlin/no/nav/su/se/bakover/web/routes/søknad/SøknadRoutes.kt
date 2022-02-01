package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.søknad.AvslåManglendeDokumentasjonRequest
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonService
import no.nav.su.se.bakover.service.søknad.KunneIkkeAvslåSøknad
import no.nav.su.se.bakover.service.søknad.KunneIkkeLageSøknadPdf
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.metrics.SuMetrics
import no.nav.su.se.bakover.web.receiveTextUTF8
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.Brev.kunneIkkeLageBrevutkast
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknad.lukk.LukkSøknadErrorHandler
import no.nav.su.se.bakover.web.routes.søknad.lukk.LukkSøknadInputHandler
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSøknadId
import java.time.Clock

internal const val søknadPath = "/soknad"

internal fun Route.søknadRoutes(
    søknadService: SøknadService,
    lukkSøknadService: LukkSøknadService,
    avslåSøknadManglendeDokumentasjonService: AvslåSøknadManglendeDokumentasjonService,
    clock: Clock,
) {
    authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
        post(søknadPath) {
            Either.catch { deserialize<SøknadInnholdJson>(call) }.fold(
                ifLeft = {
                    call.application.environment.log.info(it.message, it)
                    call.svar(Feilresponser.ugyldigBody)
                },
                ifRight = {
                    søknadService.nySøknad(it.toSøknadInnhold()).fold(
                        { kunneIkkeOppretteSøknad ->
                            call.svar(
                                when (kunneIkkeOppretteSøknad) {
                                    KunneIkkeOppretteSøknad.FantIkkePerson -> Feilresponser.fantIkkePerson
                                },
                            )
                        },
                        { (saksnummer, søknad) ->
                            call.audit(søknad.søknadInnhold.personopplysninger.fnr, AuditLogEvent.Action.CREATE, null)
                            call.sikkerlogg("Lagrer søknad ${søknad.id} på sak ${søknad.sakId}")
                            SuMetrics.søknadMottatt(
                                if (søknad.søknadInnhold.forNav is ForNav.Papirsøknad)
                                    SuMetrics.Søknadstype.PAPIR
                                else
                                    SuMetrics.Søknadstype.DIGITAL,
                            )
                            call.svar(
                                Resultat.json(
                                    Created,
                                    serialize(
                                        OpprettetSøknadJson(
                                            saksnummer = saksnummer.nummer,
                                            søknad = søknad.toJson(),
                                        ),
                                    ),
                                ),
                            )
                        },
                    )
                },
            )
        }
    }

    authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
        get("$søknadPath/{søknadId}/utskrift") {
            call.withSøknadId { søknadId ->
                søknadService.hentSøknadPdf(søknadId).fold(
                    {
                        val responseMessage = when (it) {
                            KunneIkkeLageSøknadPdf.FantIkkeSøknad -> NotFound.errorJson(
                                "Fant ikke søknad",
                                "fant_ikke_søknad",
                            )
                            KunneIkkeLageSøknadPdf.KunneIkkeLagePdf -> InternalServerError.errorJson(
                                "Kunne ikke lage PDF",
                                "kunne_ikke_lage_pdf",
                            )
                            KunneIkkeLageSøknadPdf.FantIkkePerson -> Feilresponser.fantIkkePerson
                            KunneIkkeLageSøknadPdf.FantIkkeSak -> NotFound.errorJson(
                                "Fant ikke sak",
                                "fant_ikke_sak",
                            )
                        }
                        call.respond(responseMessage)
                    },
                    {
                        call.respondBytes(it, ContentType.Application.Pdf)
                    },
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
                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                ).mapLeft {
                    call.svar(Feilresponser.ugyldigInput)
                }.map { request ->
                    lukkSøknadService.lukkSøknad(request).fold(
                        { call.svar(LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(request, it)) },
                        {
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, null)
                            call.sikkerlogg("Lukket søknad for søknad: $søknadId")
                            call.svar(Resultat.json(OK, serialize(it.toJson(clock))))
                        },
                    )
                }
            }
        }
    }

    data class WithFritekstBody(val fritekst: String)

    authorize(Brukerrolle.Saksbehandler) {
        post("$søknadPath/{søknadId}/avslag") {
            call.withSøknadId { søknadId ->
                call.withBody<WithFritekstBody> { body ->
                    avslåSøknadManglendeDokumentasjonService.avslå(
                        AvslåManglendeDokumentasjonRequest(
                            søknadId = søknadId,
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                            fritekstTilBrev = body.fritekst,
                        ),
                    ).mapLeft {
                        call.svar(
                            when (it) {
                                KunneIkkeAvslåSøknad.SøknadsbehandlingIUgyldigTilstandForAvslag -> Feilresponser.behandlingErIUgyldigTilstand
                                KunneIkkeAvslåSøknad.KunneIkkeFinneGjeldendeUtbetaling -> Feilresponser.fantIkkeGjeldendeUtbetaling
                                KunneIkkeAvslåSøknad.KunneIkkeGenererePDF -> Feilresponser.Brev.kunneIkkeGenerereBrev
                                KunneIkkeAvslåSøknad.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> Feilresponser.fantIkkeSaksbehandlerEllerAttestant
                                KunneIkkeAvslåSøknad.KunneIkkeHentePerson -> Feilresponser.fantIkkePerson
                                KunneIkkeAvslåSøknad.FantIkkeSak -> Feilresponser.fantIkkeSak
                                KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad -> Feilresponser.fantIkkeSøknad
                                KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.HarAlleredeÅpenSøknadsbehandling -> Feilresponser.harAlleredeÅpenBehandling
                                KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.SøknadErLukket -> Feilresponser.søknadErLukket
                                KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.SøknadHarAlleredeBehandling -> Feilresponser.søknadHarBehandlingFraFør
                                KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.SøknadManglerOppgave -> Feilresponser.søknadManglerOppgave
                            },
                        )
                    }.map {
                        call.svar(Resultat.json(OK, serialize(it.toJson(clock))))
                    }
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
                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                ).mapLeft {
                    call.svar(Feilresponser.ugyldigInput)
                }.map { request ->
                    lukkSøknadService.lagBrevutkast(
                        request,
                    ).fold(
                        {
                            when (it) {
                                KunneIkkeLageBrevutkast.FantIkkeSøknad ->
                                    call.svar(NotFound.errorJson("Fant Ikke Søknad", "fant_ikke_søknad"))
                                KunneIkkeLageBrevutkast.UkjentBrevtype ->
                                    call.svar(
                                        BadRequest.errorJson(
                                            "Kunne ikke lage brev for ukjent brevtype",
                                            "ukjent_brevtype",
                                        ),
                                    )
                                else ->
                                    call.svar(kunneIkkeLageBrevutkast)
                            }
                        },
                        { call.respondBytes(it, ContentType.Application.Pdf) },
                    )
                }
            }
        }
    }
}
