package no.nav.su.se.bakover.web.routes.klage

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.Brev.kunneIkkeGenerereBrev
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeKlage
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkePerson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSaksbehandlerEllerAttestant
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
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withKlageId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkeTilknytningTilSak
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevKommandoForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLeggeTilFritekstForAvvist
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeKlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenneKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.brev.KunneIkkeLageBrevForKlage
import no.nav.su.se.bakover.domain.klage.brev.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KlageVurderingerRequest
import no.nav.su.se.bakover.service.klage.NyKlageRequest
import no.nav.su.se.bakover.service.klage.UnderkjennKlageRequest
import no.nav.su.se.bakover.service.klage.VurderKlagevilkårRequest
import no.nav.su.se.bakover.web.routes.dokument.tilResultat
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal const val KLAGE_PATH = "$SAK_PATH/{sakId}/klager"

private enum class Grunn {
    INNGANGSVILKÅRENE_ER_FEILVURDERT,
    BEREGNINGEN_ER_FEIL,
    DOKUMENTASJON_MANGLER,
    VEDTAKSBREVET_ER_FEIL,
    ANDRE_FORHOLD,
}

private enum class Svarord {
    JA,
    NEI_MEN_SKAL_VURDERES,
    NEI,
}

internal fun Route.klageRoutes(
    klageService: KlageService,
    clock: Clock,
) {
    post(KLAGE_PATH) {
        authorize(Brukerrolle.Saksbehandler) {
            data class Body(val journalpostId: String, val datoKlageMottatt: LocalDate)
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    val resultat = klageService.opprett(
                        NyKlageRequest(
                            sakId = sakId,
                            saksbehandler = call.suUserContext.saksbehandler,
                            journalpostId = JournalpostId(body.journalpostId),
                            datoKlageMottatt = body.datoKlageMottatt,
                            clock = clock,
                        ),
                    ).map {
                        call.audit(it.fnr, AuditLogEvent.Action.CREATE, it.id)
                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson()))
                    }.getOrElse {
                        when (it) {
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

                            is KunneIkkeOppretteKlage.FeilVedHentingAvJournalpost -> it.feil.toErrorJson()
                        }
                    }
                    call.svar(resultat)
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/vilkår/vurderinger") {
        authorize(Brukerrolle.Saksbehandler) {
            data class Body(
                val vedtakId: UUID?,
                val innenforFristen: Svarord?,
                val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
                val erUnderskrevet: Svarord?,
            )
            call.withKlageId { klageId ->
                call.withBody<Body> { body ->
                    val resultat = klageService.vilkårsvurder(
                        VurderKlagevilkårRequest(
                            klageId = klageId,
                            saksbehandler = call.suUserContext.saksbehandler,
                            vedtakId = body.vedtakId,
                            innenforFristen = when (body.innenforFristen) {
                                Svarord.JA -> VilkårsvurderingerTilKlage.Svarord.JA
                                Svarord.NEI_MEN_SKAL_VURDERES -> VilkårsvurderingerTilKlage.Svarord.NEI_MEN_SKAL_VURDERES
                                Svarord.NEI -> VilkårsvurderingerTilKlage.Svarord.NEI
                                null -> null
                            },
                            klagesDetPåKonkreteElementerIVedtaket = body.klagesDetPåKonkreteElementerIVedtaket,
                            erUnderskrevet = when (body.erUnderskrevet) {
                                Svarord.JA -> VilkårsvurderingerTilKlage.Svarord.JA
                                Svarord.NEI_MEN_SKAL_VURDERES -> VilkårsvurderingerTilKlage.Svarord.NEI_MEN_SKAL_VURDERES
                                Svarord.NEI -> VilkårsvurderingerTilKlage.Svarord.NEI
                                null -> null
                            },
                            /*
                             * https://trello.com/c/XPMsIuNe/1168-klages-formkrav-fjerne-begrunnelsesfeltet
                             * Usikre om det blir behov for begrunnelse videre, eller om det endres til andre ting.
                             * Per nå er begrunnelse fjernet helt fra frontend
                             */
                            begrunnelse = "",
                        ),
                    ).map {
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                        Resultat.json(OK, serialize(it.toJson()))
                    }.getOrElse {
                        when (it) {
                            KunneIkkeVilkårsvurdereKlage.FantIkkeKlage -> fantIkkeKlage
                            KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak -> fantIkkeVedtak
                            is KunneIkkeVilkårsvurdereKlage.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                            KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt -> BadRequest.errorJson(
                                "Kan ikke avvise en klage som har tidligere vært oversendt",
                                "kan_ikke_avvise_klage_som_har_vært_oversendt",
                            )
                        }
                    }
                    call.svar(resultat)
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/vilkår/vurderinger/bekreft") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withKlageId { klageId ->
                val resultat = klageService.bekreftVilkårsvurderinger(
                    klageId = klageId,
                    saksbehandler = call.suUserContext.saksbehandler,
                ).map {
                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                    Resultat.json(OK, serialize(it.toJson()))
                }.getOrElse {
                    return@getOrElse when (it) {
                        KunneIkkeBekrefteKlagesteg.FantIkkeKlage -> fantIkkeKlage
                        is KunneIkkeBekrefteKlagesteg.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                    }
                }
                call.svar(resultat)
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/avvist/fritekstTilBrev") {
        authorize(Brukerrolle.Saksbehandler) {
            data class Body(val fritekst: String)
            call.withKlageId { klageId ->
                call.withBody<Body> { body ->
                    klageService.leggTilAvvistFritekstTilBrev(
                        klageId = klageId,
                        saksbehandler = call.suUserContext.saksbehandler,
                        fritekst = body.fritekst,
                    ).map {
                        call.audit(it.fnr, AuditLogEvent.Action.ACCESS, it.id)
                        call.svar(Resultat.json(OK, serialize(it.toJson())))
                    }.mapLeft {
                        val error = when (it) {
                            KunneIkkeLeggeTilFritekstForAvvist.FantIkkeKlage -> fantIkkeKlage
                            is KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                        }
                        call.svar(error)
                    }
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/brevutkast") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withKlageId { klageId ->
                klageService.brevutkast(
                    ident = call.suUserContext.hentNavIdentBruker(),
                    klageId = klageId,
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

    post("$KLAGE_PATH/{klageId}/vurderinger") {
        authorize(Brukerrolle.Saksbehandler) {
            fun KunneIkkeVurdereKlage.tilResultat(): Resultat {
                return when (this) {
                    KunneIkkeVurdereKlage.FantIkkeKlage -> fantIkkeKlage
                    KunneIkkeVurdereKlage.KanIkkeVelgeBådeOmgjørOgOppretthold -> BadRequest.errorJson(
                        "Kan ikke velge både omgjør og oppretthold.",
                        "kan_ikke_velge_både_omgjør_og_oppretthold",
                    )

                    KunneIkkeVurdereKlage.UgyldigOmgjøringsutfall -> BadRequest.errorJson(
                        "Ugyldig omgjøringsutfall",
                        "ugyldig_omgjøringsutfall",
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

            data class Omgjør(val årsak: String?, val utfall: String?)
            data class Oppretthold(val hjemler: List<String> = emptyList())

            data class Body(
                val fritekstTilBrev: String?,
                val omgjør: Omgjør?,
                val oppretthold: Oppretthold?,
            )

            call.withKlageId { klageId ->
                call.withBody<Body> { body ->
                    val resultat: Resultat = klageService.vurder(
                        request = KlageVurderingerRequest(
                            klageId = klageId,
                            fritekstTilBrev = body.fritekstTilBrev,
                            omgjør = body.omgjør?.let { o ->
                                KlageVurderingerRequest.Omgjør(
                                    årsak = o.årsak,
                                    utfall = o.utfall,
                                )
                            },
                            oppretthold = body.oppretthold?.let { o ->
                                KlageVurderingerRequest.Oppretthold(hjemler = o.hjemler)
                            },
                            saksbehandler = call.suUserContext.saksbehandler,
                        ),
                    ).map { vurdertKlage ->
                        call.audit(vurdertKlage.fnr, AuditLogEvent.Action.UPDATE, vurdertKlage.id)
                        Resultat.json(OK, serialize(vurdertKlage.toJson()))
                    }.getOrElse { error ->
                        error.tilResultat()
                    }
                    call.svar(resultat)
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/vurderinger/bekreft") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withKlageId { klageId ->
                klageService.bekreftVurderinger(
                    klageId = klageId,
                    saksbehandler = call.suUserContext.saksbehandler,
                ).map {
                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                }.mapLeft {
                    call.svar(
                        when (it) {
                            KunneIkkeBekrefteKlagesteg.FantIkkeKlage -> fantIkkeKlage
                            is KunneIkkeBekrefteKlagesteg.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                        },
                    )
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/tilAttestering") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withKlageId { klageId ->
                klageService.sendTilAttestering(klageId, call.suUserContext.saksbehandler).map {
                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                }.mapLeft {
                    call.svar(
                        when (it) {
                            KunneIkkeSendeKlageTilAttestering.FantIkkeKlage -> fantIkkeKlage
                            is KunneIkkeSendeKlageTilAttestering.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                            KunneIkkeSendeKlageTilAttestering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
                        },
                    )
                }
            }
        }
    }

    data class Body(val grunn: String, val kommentar: String) {
        fun toRequest(
            klageId: UUID,
            attestant: NavIdentBruker.Attestant,
        ): Either<Resultat, UnderkjennKlageRequest> {
            return UnderkjennKlageRequest(
                klageId = klageId,
                attestant = attestant,
                grunn = Either.catch { Grunn.valueOf(grunn) }.map {
                    when (it) {
                        Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT -> Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT
                        Grunn.BEREGNINGEN_ER_FEIL -> Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL
                        Grunn.DOKUMENTASJON_MANGLER -> Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER
                        Grunn.VEDTAKSBREVET_ER_FEIL -> Attestering.Underkjent.Grunn.VEDTAKSBREVET_ER_FEIL
                        Grunn.ANDRE_FORHOLD -> Attestering.Underkjent.Grunn.ANDRE_FORHOLD
                    }
                }.getOrElse {
                    return BadRequest.errorJson(
                        "Ugyldig underkjennelsesgrunn",
                        "ugyldig_grunn_for_underkjenning",
                    ).left()
                },
                kommentar = kommentar,
            ).right()
        }
    }
    post("$KLAGE_PATH/{klageId}/underkjenn") {
        authorize(Brukerrolle.Attestant) {
            call.withKlageId { klageId ->
                call.withBody<Body> { body ->
                    body.toRequest(klageId, call.suUserContext.attestant).map {
                        klageService.underkjenn(it).map { vurdertKlage ->
                            call.audit(vurdertKlage.fnr, AuditLogEvent.Action.UPDATE, vurdertKlage.id)
                            call.svar(Resultat.json(OK, serialize(vurdertKlage.toJson())))
                        }.mapLeft { error ->
                            call.svar(
                                when (error) {
                                    KunneIkkeUnderkjenneKlage.FantIkkeKlage -> fantIkkeKlage
                                    is KunneIkkeUnderkjenneKlage.UgyldigTilstand -> ugyldigTilstand(
                                        error.fra,
                                        error.til,
                                    )

                                    KunneIkkeUnderkjenneKlage.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
                                    KunneIkkeUnderkjenneKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                                },
                            )
                        }
                    }.mapLeft {
                        call.svar(it)
                    }
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/oversend") {
        authorize(Brukerrolle.Attestant) {
            call.withKlageId { klageId ->
                klageService.oversend(
                    klageId = klageId,
                    attestant = NavIdentBruker.Attestant(
                        call.suUserContext.navIdent,
                    ),
                ).map {
                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                }.mapLeft {
                    call.svar(
                        when (it) {
                            KunneIkkeOversendeKlage.FantIkkeKlage -> fantIkkeKlage
                            is KunneIkkeOversendeKlage.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                            KunneIkkeOversendeKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                            KunneIkkeOversendeKlage.FantIkkeJournalpostIdKnyttetTilVedtaket -> InternalServerError.errorJson(
                                "Fant ikke journalpost-id knyttet til vedtaket. Utviklingsteamet ønsker og bli informert dersom dette oppstår.",
                                "fant_ikke_journalpostid_knyttet_til_vedtaket",
                            )

                            KunneIkkeOversendeKlage.KunneIkkeOversendeTilKlageinstans -> InternalServerError.errorJson(
                                "Kunne ikke oversende til klageinstans",
                                "kunne_ikke_oversende_til_klageinstans",
                            )

                            is KunneIkkeOversendeKlage.KunneIkkeLageBrevRequest -> it.feil.toErrorJson()
                            is KunneIkkeOversendeKlage.KunneIkkeLageDokument -> it.feil.tilResultat()
                        },
                    )
                }
            }
        }
    }

    // TODO jah: Denne er i bruk, men har en litt snodig url?
    post("$KLAGE_PATH/{klageId}/iverksett(AvvistKlage)") {
        authorize(Brukerrolle.Attestant) {
            call.withKlageId { klageId ->
                klageService.iverksettAvvistKlage(
                    klageId = klageId,
                    attestant = NavIdentBruker.Attestant(
                        call.suUserContext.navIdent,
                    ),
                ).map {
                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                }.mapLeft {
                    val resultat = when (it) {
                        KunneIkkeIverksetteAvvistKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                        KunneIkkeIverksetteAvvistKlage.FantIkkeKlage -> fantIkkeKlage
                        is KunneIkkeIverksetteAvvistKlage.KunneIkkeLageBrev -> it.feil.tilResultat()
                        is KunneIkkeIverksetteAvvistKlage.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                        KunneIkkeIverksetteAvvistKlage.FeilVedLagringAvDokumentOgKlage -> InternalServerError.errorJson(
                            "Feil ved lagrinng av brev/klagen",
                            "feil_ved_lagring_av_brev_og_klage",
                        )

                        is KunneIkkeIverksetteAvvistKlage.KunneIkkeLageBrevRequest -> it.feil.toErrorJson()
                    }
                    call.svar(resultat)
                }
            }
        }
    }

    post("$KLAGE_PATH/{klageId}/avslutt") {
        data class Body(val begrunnelse: String)
        authorize(Brukerrolle.Saksbehandler) {
            call.withKlageId { klageId ->
                call.withBody<Body> { body ->
                    klageService.avslutt(
                        klageId = klageId,
                        saksbehandler = call.suUserContext.saksbehandler,
                        begrunnelse = body.begrunnelse,
                    ).map {
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                        call.svar(Resultat.json(OK, serialize(it.toJson())))
                    }.mapLeft {
                        call.svar(
                            when (it) {
                                KunneIkkeAvslutteKlage.FantIkkeKlage -> fantIkkeKlage
                                is KunneIkkeAvslutteKlage.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Klienten er ikke skyld i FantIkkePerson, FantIkkeSaksbehandler, FantIkkeVedtakKnyttetTilKlagen og KunneIkkeGenererePDF (det er ikke knyttet til input klienten sender inn) derfor mappes de til InternalServerError.
 */
fun KunneIkkeLageBrevForKlage.toErrorJson(): Resultat {
    return when (this) {
        KunneIkkeLageBrevForKlage.FantIkkePerson -> fantIkkePerson.copy(httpCode = InternalServerError)
        KunneIkkeLageBrevForKlage.FantIkkeSaksbehandler -> fantIkkeSaksbehandlerEllerAttestant.copy(httpCode = InternalServerError)
        KunneIkkeLageBrevForKlage.FantIkkeVedtakKnyttetTilKlagen -> fantIkkeVedtak.copy(httpCode = InternalServerError)
        KunneIkkeLageBrevForKlage.KunneIkkeGenererePDF -> kunneIkkeGenerereBrev.copy(httpCode = InternalServerError)
        is KunneIkkeLageBrevForKlage.UgyldigTilstand -> return BadRequest.errorJson(
            "Kan ikke lagre brevutkast for tilstanden ${fra.simpleName}",
            "genererer_brev_fra_ugyldig_tilstand",
        )

        is KunneIkkeLageBrevForKlage.FeilVedBrevRequest -> this.feil.toErrorJson()
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
    }
}

fun KunneIkkeLageBrevutkast.toErrorJson(): Resultat {
    return when (this) {
        KunneIkkeLageBrevutkast.FantIkkeKlage -> fantIkkeKlage
        is KunneIkkeLageBrevutkast.FeilVedBrevRequest -> this.feil.toErrorJson()
        is KunneIkkeLageBrevutkast.KunneIkkeGenererePdf -> this.feil.tilResultat()
    }
}

fun KunneIkkeSjekkeTilknytningTilSak.toErrorJson(): Resultat {
    return when (this) {
        KunneIkkeSjekkeTilknytningTilSak.FantIkkeJournalpost -> BadRequest.errorJson(
            "Fant ikke journalpost",
            "fant_ikke_journalpost",
        )

        KunneIkkeSjekkeTilknytningTilSak.IkkeTilgang -> Unauthorized.errorJson(
            "Ikke tilgang til Journalpost",
            "ikke_tilgang_til_journalpost",
        )

        KunneIkkeSjekkeTilknytningTilSak.TekniskFeil -> InternalServerError.errorJson(
            "Teknisk feil ved henting av journalpost",
            "teknisk_feil_ved_henting_av_journalpost",
        )

        KunneIkkeSjekkeTilknytningTilSak.Ukjent -> InternalServerError.errorJson(
            "Ukjent feil ved henting av journalpost",
            "ukjent_feil_ved_henting_av_journalpost",
        )

        KunneIkkeSjekkeTilknytningTilSak.UgyldigInput -> BadRequest.errorJson(
            "Ugyldig journalpostId",
            "ugyldig_journalpostId",
        )

        KunneIkkeSjekkeTilknytningTilSak.JournalpostIkkeKnyttetTilSak -> BadRequest.errorJson(
            "Journalposten er ikke knyttet til saken",
            "journalpost_ikke_knyttet_til_sak",
        )
    }
}
