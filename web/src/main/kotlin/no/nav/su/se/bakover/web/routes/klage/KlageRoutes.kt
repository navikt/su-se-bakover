package no.nav.su.se.bakover.web.routes.klage

import arrow.core.getOrElse
import arrow.core.merge
import arrow.core.raise.either
import behandling.klage.domain.FormkravTilKlage
import behandling.klage.domain.KlageId
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeAdresse
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeKlage
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeVedtak
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.feilVedHentingAvVedtakDato
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withKlageId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeFerdigstilleOmgjøringsKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevKommandoForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLeggeTilFritekstForAvvist
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeKlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenneKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.brev.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.presentation.web.toErrorJson
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KlageVurderingerRequest
import no.nav.su.se.bakover.service.klage.NyKlageRequest
import no.nav.su.se.bakover.service.klage.UgyldigNyKlageRequest
import no.nav.su.se.bakover.service.klage.VurderKlagevilkårCommand
import no.nav.su.se.bakover.web.inputvalidation.sti
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.dokument.tilResultat
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal const val KLAGE_PATH = "$SAK_PATH/{sakId}/klager"

data class OpprettKlageRequest(
    val journalpostId: String,
    val datoKlageMottatt: LocalDate,
    val relatertBehandlingId: String?,
    val erInfotrygdSakId: String?,
)

internal data class VilkårsvurderKlageBody(
    val vedtakId: UUID?,
    val innenforFristen: FormkravTilKlage.SvarMedBegrunnelse?,
    val klagesDetPåKonkreteElementerIVedtaket: FormkravTilKlage.BooleanMedBegrunnelse?,
    val erUnderskrevet: FormkravTilKlage.SvarMedBegrunnelse?,
    val fremsattRettsligKlageinteresse: FormkravTilKlage.SvarMedBegrunnelse?,
    val infotrygdSakId: String?,
)

internal data class AvvistKlageFritekstBody(val fritekst: String)

internal data class BehandlesIVedtaksinstansBody(val årsak: String?, val begrunnelse: String?)
internal data class SkalTilKabalBody(val hjemler: List<String> = emptyList(), val klagenotat: String?)

internal data class VurderKlageBody(
    val fritekstTilBrev: String?,
    val omgjør: BehandlesIVedtaksinstansBody?,
    val delvisomgjøring_egen_instans: BehandlesIVedtaksinstansBody?,
    val oppretthold: SkalTilKabalBody?,
    val delvisomgjøringKa: SkalTilKabalBody?,
)

internal data class AvsluttKlageBody(val begrunnelse: String)

internal fun Route.klageRoutes(
    klageService: KlageService,
    clock: Clock,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post(KLAGE_PATH) {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withValidertBody<OpprettKlageRequest>(
                    log,
                    maksLengde = mapOf(sti(OpprettKlageRequest::erInfotrygdSakId) to INFOTRYGD_SAK_ID_LENGDE),
                ) { body ->
                    either {
                        val request = NyKlageRequest.tryCreate(
                            sakId = sakId,
                            saksbehandler = call.suUserContext.saksbehandler,
                            journalpostId = JournalpostId(body.journalpostId),
                            datoKlageMottatt = body.datoKlageMottatt,
                            relatertBehandlingId = body.relatertBehandlingId,
                            erInfotrygdSakId = body.erInfotrygdSakId,
                            clock = clock,
                        )
                            .mapLeft { ugyldigNyKlageRequest -> ugyldigNyKlageRequest.tilFeilmelding() }
                            .bind()

                        val klage = klageService.opprett(request)
                            .mapLeft { kunneIkkeOppretteKlage -> kunneIkkeOppretteKlage.tilFeilmelding() }
                            .bind()

                        call.audit(
                            klage.fnr,
                            AuditLogEvent.Action.CREATE,
                            klage.id.value,
                        )
                        Resultat.json(
                            HttpStatusCode.Created,
                            serialize(klage.toJson()),
                        )
                    }
                        .merge()
                        .let { resultat -> call.svar(resultat) }
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/vilkår/vurderinger") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withKlageId { klageId ->
                    call.withValidertBody<VilkårsvurderKlageBody>(
                        log,
                        maksLengde = mapOf(sti(VilkårsvurderKlageBody::infotrygdSakId) to INFOTRYGD_SAK_ID_LENGDE),
                    ) { body ->
                        either {
                            val vilkårsvurdertKlage = klageService.vilkårsvurder(
                                VurderKlagevilkårCommand(
                                    klageId = KlageId(klageId),
                                    saksbehandler = call.suUserContext.saksbehandler,
                                    vedtakId = body.vedtakId,
                                    innenforFristen = body.innenforFristen,
                                    klagesDetPåKonkreteElementerIVedtaket = body.klagesDetPåKonkreteElementerIVedtaket,
                                    erUnderskrevet = body.erUnderskrevet,
                                    sakId = sakId,
                                    fremsattRettsligKlageinteresse = body.fremsattRettsligKlageinteresse,
                                    infotrygdSakId = body.infotrygdSakId,
                                ),
                            )
                                .mapLeft { kunneIkkeVilkårsvurdereKlage -> kunneIkkeVilkårsvurdereKlage.tilFeilmelding() }
                                .bind()

                            call.audit(
                                vilkårsvurdertKlage.fnr,
                                AuditLogEvent.Action.UPDATE,
                                vilkårsvurdertKlage.id.value,
                            )
                            Resultat.json(
                                OK,
                                serialize(vilkårsvurdertKlage.toJson()),
                            )
                        }
                            .merge()
                            .let { resultat -> call.svar(resultat) }
                    }
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/vilkår/vurderinger/bekreft") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withKlageId { klageId ->
                val resultat = klageService.bekreftVilkårsvurderinger(
                    klageId = KlageId(klageId),
                    saksbehandler = call.suUserContext.saksbehandler,
                ).map {
                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                    Resultat.json(OK, serialize(it.toJson()))
                }.getOrElse {
                    return@getOrElse when (it) {
                        KunneIkkeBekrefteKlagesteg.FantIkkeKlage -> fantIkkeKlage
                        is KunneIkkeBekrefteKlagesteg.UgyldigTilstand -> ugyldigTilstand(
                            it.fra,
                            it.til,
                        )
                    }
                }
                call.svar(resultat)
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/avvist/fritekstTilBrev") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withKlageId { klageId ->
                call.withValidertBody<AvvistKlageFritekstBody>(log) { body ->
                    either {
                        val avvistKlage = klageService.leggTilAvvistFritekstTilBrev(
                            klageId = KlageId(klageId),
                            saksbehandler = call.suUserContext.saksbehandler,
                            fritekst = body.fritekst,
                        )
                            .mapLeft {
                                when (it) {
                                    KunneIkkeLeggeTilFritekstForAvvist.FantIkkeKlage -> fantIkkeKlage
                                    is KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand -> ugyldigTilstand(
                                        it.fra,
                                        it.til,
                                    )
                                }
                            }
                            .bind()

                        call.audit(
                            avvistKlage.fnr,
                            AuditLogEvent.Action.ACCESS,
                            avvistKlage.id.value,
                        )
                        Resultat.json(
                            OK,
                            serialize(avvistKlage.toJson()),
                        )
                    }
                        .merge()
                        .let { resultat -> call.svar(resultat) }
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/brevutkast") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withKlageId { klageId ->
                    klageService.brevutkast(
                        sakId = sakId,
                        ident = call.suUserContext.hentNavIdentBruker(),
                        klageId = KlageId(klageId),
                    ).fold(
                        ifLeft = {
                            call.svar(it.toErrorJson())
                        },
                        ifRight = {
                            call.respondBytes(it.getContent(), ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/vurderinger") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withKlageId { klageId ->
                call.withValidertBody<VurderKlageBody>(log) { body ->
                    either {
                        val vurdertKlage = klageService.vurder(
                            request = KlageVurderingerRequest(
                                klageId = KlageId(klageId),
                                fritekstTilBrev = body.fritekstTilBrev,
                                omgjør = body.omgjør?.let { o ->
                                    KlageVurderingerRequest.BehandlesIVedtaksInstansen(
                                        årsak = o.årsak,
                                        begrunnelse = o.begrunnelse,
                                        erDelvisOmgjøring = false,
                                    )
                                },
                                delvisomgjøring_egen_instans = body.delvisomgjøring_egen_instans?.let { o ->
                                    KlageVurderingerRequest.BehandlesIVedtaksInstansen(
                                        årsak = o.årsak,
                                        begrunnelse = o.begrunnelse,
                                        erDelvisOmgjøring = true,
                                    )
                                },
                                oppretthold = body.oppretthold?.let { oppretthold ->
                                    KlageVurderingerRequest.SkalTilKabal(
                                        hjemler = oppretthold.hjemler,
                                        klagenotat = oppretthold.klagenotat,
                                        erOppretthold = true,
                                    )
                                },
                                delvisomgjøring_ka = body.delvisomgjøringKa?.let { delvisOmgjøring ->
                                    KlageVurderingerRequest.SkalTilKabal(
                                        hjemler = delvisOmgjøring.hjemler,
                                        klagenotat = delvisOmgjøring.klagenotat,
                                        erOppretthold = false,
                                    )
                                },
                                saksbehandler = call.suUserContext.saksbehandler,
                            ),
                        )
                            .mapLeft { kunneIkkeVurdereKlage -> kunneIkkeVurdereKlage.tilFeilmelding() }
                            .bind()

                        call.audit(vurdertKlage.fnr, AuditLogEvent.Action.UPDATE, vurdertKlage.id.value)
                        Resultat.json(OK, serialize(vurdertKlage.toJson()))
                    }.merge()
                        .let { resultat -> call.svar(resultat) }
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/vurderinger/bekreft") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withKlageId { klageId ->
                klageService.bekreftVurderinger(
                    klageId = KlageId(klageId),
                    saksbehandler = call.suUserContext.saksbehandler,
                ).map {
                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                }.mapLeft {
                    call.svar(
                        when (it) {
                            KunneIkkeBekrefteKlagesteg.FantIkkeKlage -> fantIkkeKlage
                            is KunneIkkeBekrefteKlagesteg.UgyldigTilstand -> ugyldigTilstand(
                                it.fra,
                                it.til,
                            )
                        },
                    )
                }
            }
        }
    }
    /*
    Gjelder kun klager som skal til KA. Omgjøring skal ikke sendes til KA, de må også gjennom
    attestering i omgjøringsbehandlingen.
     */
    post("$KLAGE_PATH/{klageId}/tilAttestering") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withKlageId { klageId ->
                klageService.sendTilAttestering(KlageId(klageId), call.suUserContext.saksbehandler).map {
                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                }.mapLeft {
                    call.svar(
                        when (it) {
                            KunneIkkeSendeKlageTilAttestering.FantIkkeKlage -> fantIkkeKlage
                            is KunneIkkeSendeKlageTilAttestering.UgyldigTilstand -> ugyldigTilstand(
                                it.fra,
                                it.til,
                            )

                            KunneIkkeSendeKlageTilAttestering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
                        },
                    )
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/ferdigstill") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withKlageId { klageId ->
                klageService.ferdigstillOmgjøring(KlageId(klageId), call.suUserContext.saksbehandler).map {
                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                }.mapLeft {
                    call.svar(
                        when (it) {
                            KunneIkkeFerdigstilleOmgjøringsKlage.FantIkkeKlage -> fantIkkeKlage
                            is KunneIkkeFerdigstilleOmgjøringsKlage.UgyldigTilstand -> ugyldigTilstand(
                                it.fra,
                                it.til,
                            )

                            KunneIkkeFerdigstilleOmgjøringsKlage.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
                        },
                    )
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/underkjenn") {
        authorize(Brukerrolle.Attestant) {
            call.withKlageId { klageId ->
                call.withValidertBody<KlageUnderkjentBody>(
                    log,
                    maksLengde = mapOf(sti(KlageUnderkjentBody::grunn) to 100),
                ) { body ->
                    either {
                        val underkjennKlageRequest = body
                            .toRequest(klageId, call.suUserContext.attestant)
                            .bind()

                        val vurdertKlage = klageService
                            .underkjenn(underkjennKlageRequest)
                            .mapLeft { kunneIkkeUnderkjenneKlage ->
                                when (kunneIkkeUnderkjenneKlage) {
                                    KunneIkkeUnderkjenneKlage.FantIkkeKlage -> fantIkkeKlage
                                    is KunneIkkeUnderkjenneKlage.UgyldigTilstand -> ugyldigTilstand(
                                        kunneIkkeUnderkjenneKlage.fra,
                                        kunneIkkeUnderkjenneKlage.til,
                                    )

                                    KunneIkkeUnderkjenneKlage.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
                                    KunneIkkeUnderkjenneKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                                }
                            }
                            .bind()

                        call.audit(vurdertKlage.fnr, AuditLogEvent.Action.UPDATE, vurdertKlage.id.value)
                        Resultat.json(OK, serialize(vurdertKlage.toJson()))
                    }
                        .merge()
                        .let { resultat -> call.svar(resultat) }
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/oversend") {
        authorize(Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withKlageId { klageId ->
                    klageService.oversend(
                        sakId = sakId,
                        klageId = KlageId(klageId),
                        attestant = NavIdentBruker.Attestant(
                            call.suUserContext.navIdent,
                        ),
                    ).map {
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                        call.svar(Resultat.json(OK, serialize(it.toJson())))
                    }.mapLeft {
                        call.svar(
                            when (it) {
                                KunneIkkeOversendeKlage.FantIkkeKlage -> fantIkkeKlage
                                is KunneIkkeOversendeKlage.UgyldigTilstand -> ugyldigTilstand(
                                    it.fra,
                                    it.til,
                                )

                                KunneIkkeOversendeKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                                KunneIkkeOversendeKlage.FantIkkeJournalpostIdKnyttetTilVedtaket -> InternalServerError.errorJson(
                                    "Fant ikke journalpost-id knyttet til vedtaket. Utviklingsteamet ønsker og bli informert dersom dette oppstår.",
                                    "fant_ikke_journalpostid_knyttet_til_vedtaket",
                                )

                                KunneIkkeOversendeKlage.KunneIkkeOversendeTilKlageinstans -> InternalServerError.errorJson(
                                    "Kunne ikke oversende til klageinstans",
                                    "kunne_ikke_oversende_til_klageinstans",
                                )

                                KunneIkkeOversendeKlage.ManglerVedtakId -> BadRequest.errorJson(
                                    "Mangler vedtakId",
                                    "mangler_vedtak_id",
                                )

                                is KunneIkkeOversendeKlage.KunneIkkeLageBrevRequest -> it.feil.toErrorJson()
                                is KunneIkkeOversendeKlage.KunneIkkeLageDokument -> it.feil.tilResultat()
                                is KunneIkkeOversendeKlage.FantIkkeAdresseTilBruker -> fantIkkeAdresse
                            },
                        )
                    }
                }
            }
        }
    }

    /* TODO jah: Denne er i bruk, men har en litt snodig url?
        Denne brukes kun ifbm med avvisning av formkrav.
     */
    post("$KLAGE_PATH/{klageId}/iverksett(AvvistKlage)") {
        authorize(Brukerrolle.Attestant) {
            call.withKlageId { klageId ->
                klageService.iverksettAvvistKlage(
                    klageId = KlageId(klageId),
                    attestant = NavIdentBruker.Attestant(
                        call.suUserContext.navIdent,
                    ),
                ).map {
                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                }.mapLeft {
                    val resultat = when (it) {
                        KunneIkkeIverksetteAvvistKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                        KunneIkkeIverksetteAvvistKlage.FantIkkeKlage -> fantIkkeKlage
                        is KunneIkkeIverksetteAvvistKlage.KunneIkkeLageBrev -> it.feil.tilResultat()
                        is KunneIkkeIverksetteAvvistKlage.UgyldigTilstand -> ugyldigTilstand(
                            it.fra,
                            it.til,
                        )

                        KunneIkkeIverksetteAvvistKlage.FeilVedLagringAvDokumentOgKlage -> InternalServerError.errorJson(
                            "Feil ved lagrinng av brev/klagen",
                            "feil_ved_lagring_av_brev_og_klage",
                        )

                        is KunneIkkeIverksetteAvvistKlage.KunneIkkeLageBrevRequest -> it.feil.toErrorJson()
                        is KunneIkkeIverksetteAvvistKlage.FantIkkeAdresseTilBruker -> fantIkkeAdresse
                    }
                    call.svar(resultat)
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/avslutt") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withKlageId { klageId ->
                call.withValidertBody<AvsluttKlageBody>(log) { body ->
                    either {
                        val avsluttetKlage = klageService.avslutt(
                            klageId = KlageId(klageId),
                            saksbehandler = call.suUserContext.saksbehandler,
                            begrunnelse = body.begrunnelse,
                        )
                            .mapLeft { kunneIkkeAvslutteKlage ->
                                when (kunneIkkeAvslutteKlage) {
                                    KunneIkkeAvslutteKlage.FantIkkeKlage -> fantIkkeKlage
                                    is KunneIkkeAvslutteKlage.UgyldigTilstand -> ugyldigTilstandInternalServerError(
                                        kunneIkkeAvslutteKlage.fra,
                                        kunneIkkeAvslutteKlage.til,
                                    )
                                }
                            }
                            .bind()
                        call.audit(avsluttetKlage.fnr, AuditLogEvent.Action.UPDATE, avsluttetKlage.id.value)
                        Resultat.json(OK, serialize(avsluttetKlage.toJson()))
                    }
                        .merge()
                        .let { resultat -> call.svar(resultat) }
                }
            }
        }
    }
}

// konsistent rimelig lengde for infotrygd saksid
internal const val INFOTRYGD_SAK_ID_LENGDE = 20

fun UgyldigNyKlageRequest.tilFeilmelding(): Resultat {
    val feil = when (this) {
        UgyldigNyKlageRequest.JournalpostId -> this.feilMelding
        is UgyldigNyKlageRequest.RelatertBehandlingId -> this.feilMelding
        UgyldigNyKlageRequest.ManglendeRelatertBehandlingIdEllerEksternSak -> this.feilMelding
        UgyldigNyKlageRequest.KanIkkeHaBegge -> this.feilMelding
    }
    return BadRequest.errorJson(feil, "ugyldig_parameter")
}

fun KunneIkkeOppretteKlage.tilFeilmelding(): Resultat {
    return when (this) {
        KunneIkkeOppretteKlage.FantIkkeSak -> fantIkkeSak
        KunneIkkeOppretteKlage.FinnesAlleredeEnÅpenKlage -> BadRequest.errorJson(
            "Det finnes allerede en åpen klage",
            "finnes_allerede_en_åpen_klage",
        )

        KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
        KunneIkkeOppretteKlage.UgyldigMottattDato -> BadRequest.errorJson(
            "Mottatt dato kan ikke være frem i tid",
            "ugyldig_mottatt_dato",
        )

        is KunneIkkeOppretteKlage.FeilVedHentingAvJournalpost -> this.feil.toErrorJson()
    }
}

fun KunneIkkeVurdereKlage.tilFeilmelding(): Resultat {
    return when (this) {
        KunneIkkeVurdereKlage.FantIkkeKlage -> fantIkkeKlage
        KunneIkkeVurdereKlage.ForMangeUtfall -> BadRequest.errorJson(
            "Kan ikke velge både omgjør og oppretthold.",
            "kan_ikke_velge_både_omgjør_og_oppretthold",
        )

        KunneIkkeVurdereKlage.UgyldigOmgjøringsårsak -> BadRequest.errorJson(
            "Ugyldig omgjøringsårsak",
            "ugyldig_omgjøringsårsak",
        )

        KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler -> BadRequest.errorJson(
            "Ugyldig opprettholdelseshjemler",
            "ugyldig_opprettholdelseshjemler",
        )

        is KunneIkkeVurdereKlage.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
    }
}

fun KunneIkkeVilkårsvurdereKlage.tilFeilmelding(): Resultat {
    return when (this) {
        KunneIkkeVilkårsvurdereKlage.FantIkkeKlage -> fantIkkeKlage
        KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak -> fantIkkeVedtak
        is KunneIkkeVilkårsvurdereKlage.UgyldigTilstand -> ugyldigTilstand(
            this.fra,
            this.til,
        )

        KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt -> BadRequest.errorJson(
            "Kan ikke avvise en klage som har tidligere vært oversendt",
            "kan_ikke_avvise_klage_som_har_vært_oversendt",
        )

        KunneIkkeVilkårsvurdereKlage.VedtakSkalIkkeSendeBrev -> BadRequest.errorJson(
            "Vedtak som ikke skal sende brev mangler dato-felt for klage-vedtak. Disse vedtakene kan ikke klages på",
            "vedtak_skal_ikke_sende_brev",
        )
    }
}

fun KunneIkkeLageBrevKommandoForKlage.toErrorJson(): Resultat {
    return when (this) {
        KunneIkkeLageBrevKommandoForKlage.FeilVedHentingAvVedtaksbrevDato,
        -> feilVedHentingAvVedtakDato
        is KunneIkkeLageBrevKommandoForKlage.UgyldigTilstand -> BadRequest.errorJson(
            "Kan ikke gå fra tilstanden ${fra.simpleName}",
            "ugyldig_tilstand",
        )

        KunneIkkeLageBrevKommandoForKlage.FritekstErIkkeFyltUt -> BadRequest.errorJson(
            "Fritekst er ikke fylt ut",
            "fritekst_er_ikke_fylt_ut",
        )

        KunneIkkeLageBrevKommandoForKlage.ManglerVedtakId -> BadRequest.errorJson(
            "Mangler vedtakId",
            "mangler_vedtak_id",
        )
    }
}

fun KunneIkkeLageBrevutkast.toErrorJson(): Resultat {
    return when (this) {
        KunneIkkeLageBrevutkast.FantIkkeKlage -> fantIkkeKlage
        is KunneIkkeLageBrevutkast.FeilVedBrevRequest -> this.feil.toErrorJson()
        is KunneIkkeLageBrevutkast.KunneIkkeGenererePdf -> this.feil.tilResultat()
    }
}
