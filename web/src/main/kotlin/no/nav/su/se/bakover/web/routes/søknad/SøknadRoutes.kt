package no.nav.su.se.bakover.web.routes.søknad

import behandling.søknadsbehandling.presentation.tilResultat
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSøknad
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.receiveTextUTF8
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withStringParam
import no.nav.su.se.bakover.common.infrastructure.web.withSøknadId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedOpprettelseAvBoforhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedOpprettelseAvFormue
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedOpprettelseAvOppholdstillatelse
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedOpprettelseAvSøknadinnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedValideringAvBoforholdOgEktefelle
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.ForNav
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.AvslåManglendeDokumentasjonCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.KunneIkkeAvslåSøknad
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonService
import no.nav.su.se.bakover.service.søknad.KunneIkkeLageSøknadPdf
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknad.lukk.LukkSøknadInputHandler
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FeilVedOpprettelseAvEktefelleJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.KunneIkkeLageSøknadinnhold
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SøknadsbehandlingJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.attester.tilResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.iverksett.tilResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.tilResultat
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

enum class Søknadstype(val value: String) {
    ALDER("alder"),
    UFØRE("ufore"),
}

const val SØKNAD_PATH = "/soknad"

internal fun Route.søknadRoutes(
    søknadService: SøknadService,
    lukkSøknadService: LukkSøknadService,
    avslåSøknadManglendeDokumentasjonService: AvslåSøknadManglendeDokumentasjonService,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
) {
    post("$SØKNAD_PATH/{type}") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
            call.withStringParam("type") { type ->
                call.withBody<SøknadsinnholdJson> { søknadsinnholdJson ->
                    søknadsinnholdJson.toSøknadsinnhold().fold(
                        { call.svar(it.tilResultat()) },
                        {
                            søknadService.nySøknad(it, søknadsinnholdJson.forNav.identBruker(call))
                                .fold(
                                    { call.svar(it.tilResultat(type)) },
                                    { (saksnummer, søknad) ->
                                        call.audit(søknad.fnr, AuditLogEvent.Action.CREATE, null)
                                        call.sikkerlogg("Lagrer søknad ${søknad.id} på sak ${søknad.sakId}")
                                        SuMetrics.søknadMottatt(
                                            if (søknad.søknadInnhold.forNav is ForNav.Papirsøknad) {
                                                SuMetrics.Søknadstype.PAPIR
                                            } else {
                                                SuMetrics.Søknadstype.DIGITAL
                                            },
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
        }
    }

    get("$SØKNAD_PATH/{søknadId}/utskrift") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
            call.withSøknadId { søknadId ->
                søknadService.hentSøknadPdf(søknadId).fold(
                    {
                        val responseMessage = when (it) {
                            KunneIkkeLageSøknadPdf.FantIkkeSøknad -> fantIkkeSøknad

                            KunneIkkeLageSøknadPdf.KunneIkkeLagePdf -> InternalServerError.errorJson(
                                "Kunne ikke lage PDF",
                                "kunne_ikke_lage_pdf",
                            )

                            KunneIkkeLageSøknadPdf.FantIkkePerson -> Feilresponser.fantIkkePerson
                            KunneIkkeLageSøknadPdf.FantIkkeSak -> fantIkkeSak
                        }
                        call.svar(responseMessage)
                    },
                    {
                        // TODO jah: audit?
                        call.respondBytes(it.getContent(), ContentType.Application.Pdf)
                    },
                )
            }
        }
    }

    post("$SØKNAD_PATH/{søknadId}/lukk") {
        data class LukkSøknadResponse(
            val lukketSøknad: SøknadJson,
            val lukketSøknadsbehandling: SøknadsbehandlingJson?,
        )

        authorize(Brukerrolle.Saksbehandler) {
            call.withSøknadId { søknadId ->
                LukkSøknadInputHandler.handle(
                    body = call.receiveTextUTF8(),
                    søknadId = søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                    clock = clock,
                ).mapLeft {
                    call.svar(Feilresponser.ugyldigInput)
                }.map { request ->
                    lukkSøknadService.lukkSøknad(request).let {
                        call.audit(
                            berørtBruker = it.third,
                            action = AuditLogEvent.Action.UPDATE,
                            behandlingId = it.second?.id?.value ?: it.first.id,
                        )
                        call.sikkerlogg("Lukket søknad for søknad: $søknadId")
                        call.svar(
                            Resultat.json(
                                OK,
                                serialize(
                                    LukkSøknadResponse(
                                        lukketSøknad = it.first.toJson(),
                                        lukketSøknadsbehandling = it.second?.toJson(formuegrenserFactory),
                                    ),
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }

    data class WithFritekstBody(val fritekst: String)

    post("$SØKNAD_PATH/{søknadId}/avslag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSøknadId { søknadId ->
                call.withBody<WithFritekstBody> { body ->
                    avslåSøknadManglendeDokumentasjonService.avslå(
                        AvslåManglendeDokumentasjonCommand(
                            søknadId = søknadId,
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                            fritekstTilBrev = body.fritekst,
                        ),
                    ).mapLeft {
                        call.svar(it.tilResultat())
                    }.map {
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, søknadId)
                        call.svar(Resultat.json(OK, serialize(it.toJson(clock, formuegrenserFactory))))
                    }
                }
            }
        }
    }

    post("$SØKNAD_PATH/{søknadId}/avslag/brevutkast") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSøknadId { søknadId ->
                call.withBody<WithFritekstBody> { body ->
                    avslåSøknadManglendeDokumentasjonService.genererBrevForhåndsvisning(
                        AvslåManglendeDokumentasjonCommand(
                            søknadId = søknadId,
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                            fritekstTilBrev = body.fritekst,
                        ),
                    ).mapLeft {
                        call.svar(it.tilResultat())
                    }.map {
                        call.audit(it.first, AuditLogEvent.Action.ACCESS, søknadId)
                        call.respondBytes(it.second.getContent(), ContentType.Application.Pdf)
                    }
                }
            }
        }
    }

    post("$SØKNAD_PATH/{søknadId}/lukk/brevutkast") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSøknadId { søknadId ->
                LukkSøknadInputHandler.handle(
                    body = call.receiveTextUTF8(),
                    søknadId = søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                    clock = clock,
                ).mapLeft {
                    call.svar(Feilresponser.ugyldigInput)
                }.map { request ->
                    lukkSøknadService.lagBrevutkast(
                        request,
                    ).let {
                        call.audit(
                            berørtBruker = it.first,
                            action = AuditLogEvent.Action.ACCESS,
                            // TODO jah: Det kan hende vi også finnes en søknadsbehandling (som også kan bli lukket), men vi mangler søknadsbehandlings id i denne konteksten.
                            behandlingId = søknadId,
                        )
                        call.respondBytes(it.second.getContent(), ContentType.Application.Pdf)
                    }
                }
            }
        }
    }
}

fun KunneIkkeOppretteSøknad.tilResultat(type: String) = when (this) {
    KunneIkkeOppretteSøknad.FantIkkePerson -> Feilresponser.fantIkkePerson
    KunneIkkeOppretteSøknad.SøknadsinnsendingIkkeTillatt -> Forbidden.errorJson(
        "Innsending av type søknad $type, er ikke tillatt",
        "innsending_av_søknad_ikke_tillatt",
    )
}

private fun KunneIkkeLageSøknadinnhold.tilResultat() = when (this) {
    is KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvOppholdstillatelseWeb -> underliggendeFeil.tilResultat()
    is KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvBoforholdWeb -> underliggendeFeil.tilResultat()
    is KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvFormueWeb -> underliggendeFeil.tilResultat()
    is KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvEktefelleWeb -> underliggendeFeil.tilResultat()
    is KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvSøknadinnholdWeb -> underliggendeFeil.tilResultat()
}

private fun FeilVedOpprettelseAvSøknadinnhold.tilResultat() = when (this) {
    is FeilVedOpprettelseAvSøknadinnhold.DataVedBoforholdOgEktefelleErInkonsekvent -> underliggendeFeil.tilResultat()
    is FeilVedOpprettelseAvSøknadinnhold.DataVedOpphodlstillatelseErInkonsekvent -> underliggendeFeil.tilResultat()
}

private fun FeilVedOpprettelseAvOppholdstillatelse.tilResultat() = when (this) {
    FeilVedOpprettelseAvOppholdstillatelse.FritekstForStatsborgerskapErIkkeUtfylt -> BadRequest.errorJson(
        "Forventet fritekst for statsborgerskap",
        "fritekst_for_statsborgerskap_er_ikke_utfylt",
    )

    FeilVedOpprettelseAvOppholdstillatelse.OppholdstillatelseErIkkeUtfylt -> BadRequest.errorJson(
        "Forventet at oppholdstillatelse er valgt",
        "oppholdstillatelse_er_ikke_utfylt",
    )

    FeilVedOpprettelseAvOppholdstillatelse.TypeOppholdstillatelseErIkkeUtfylt -> BadRequest.errorJson(
        "Forventet type oppholdstillatelse",
        "type_oppholdstillatelse_er_ikke_utfylt",
    )
}

private fun FeilVedOpprettelseAvBoforhold.tilResultat() = when (this) {
    FeilVedOpprettelseAvBoforhold.DelerBoligMedErIkkeUtfylt -> BadRequest.errorJson(
        "DelerBoligMed må være utfylt",
        "deler_bolig_med_er_ikke_utfylt",
    )

    FeilVedOpprettelseAvBoforhold.EktefellePartnerSamboerMåVæreUtfylt -> BadRequest.errorJson(
        "EktefellePartnerSamboer må være utfylt",
        "ektefelle_partner_samboer_er_ikke_utfylt",
    )

    FeilVedOpprettelseAvBoforhold.BeggeAdressegrunnerErUtfylt -> BadRequest.errorJson(
        "Kun én adressegrunn kan være utfylt",
        "kun_en_adressegrunn_kan_være_utfylt",
    )

    FeilVedOpprettelseAvBoforhold.InkonsekventInnleggelse -> BadRequest.errorJson(
        "Inkonsekvent data er sendt inn for institusjonsopphold",
        "inkonsekvent_innleggelse",
    )
}

private fun FeilVedOpprettelseAvFormue.tilResultat() = when (this) {
    FeilVedOpprettelseAvFormue.BoligensVerdiEllerBeskrivelseErIkkeUtfylt -> BadRequest.errorJson(
        "Boligens verdi/beskrivelse er ikke utfylt",
        "boligens_verdi_eller_beskrivelse_er_ikke_utfylt",
    )

    FeilVedOpprettelseAvFormue.BorIBoligErIkkeUtfylt -> BadRequest.errorJson(
        "BorIBolig må være utfylt",
        "borIBolig_er_ikke_utfylt",
    )

    FeilVedOpprettelseAvFormue.DepositumsbeløpetErIkkeutfylt -> BadRequest.errorJson(
        "Depositumsbeløpet er ikke utfylt",
        "depositumsbeløp_er_ikke_utfylt",
    )
}

private fun FeilVedOpprettelseAvEktefelleJson.tilResultat() = when (this) {
    is FeilVedOpprettelseAvEktefelleJson.FeilVedOpprettelseAvFormueEktefelle -> underliggendeFeil.tilResultat()
}

private fun FeilVedValideringAvBoforholdOgEktefelle.tilResultat() = when (this) {
    FeilVedValideringAvBoforholdOgEktefelle.EktefelleErIkkeutfylt -> BadRequest.errorJson(
        "Det er registrert at søker har EPS, men ingen data for EPS er registrert",
        "ektefelle_er_ikke_utfylt",
    )
}

private fun FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder.tilResultat() = when (this) {
    FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder.EøsBorgerErIkkeutfylt -> BadRequest.errorJson(
        "Eøs-borger er ikke utfylt",
        "eøs_borger_er_ikke_utfylt",
    )

    FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder.FamiliegjenforeningErIkkeutfylt -> BadRequest.errorJson(
        "Familiegjenforening er ikke utfylt",
        "familiegjenforening_er_ikke_utfylt",
    )
}

internal fun KunneIkkeAvslåSøknad.tilResultat(): Resultat = when (this) {
    is KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling -> this.underliggendeFeil.tilResultat()
    is KunneIkkeAvslåSøknad.KunneIkkeIverksetteSøknadsbehandling -> this.underliggendeFeil.tilResultat()
    is KunneIkkeAvslåSøknad.Attesteringsfeil -> this.feil.tilResultat()
    is KunneIkkeAvslåSøknad.Periodefeil -> this.underliggende.tilResultat()
}

/*private fun KunneIkkeSendeSøknadsbehandlingTilAttestering.tilResultat(): Resultat = when (this) {
    ValideringsfeilAttestering.InneholderUfullstendigBosituasjon -> Feilresponser.inneholderUfullstendigeBosituasjoner
}*/
