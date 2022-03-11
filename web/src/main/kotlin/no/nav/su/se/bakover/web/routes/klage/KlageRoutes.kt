package no.nav.su.se.bakover.web.routes.klage

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevRequest
import no.nav.su.se.bakover.domain.klage.KunneIkkeLeggeTilFritekstForAvvist
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KlageVurderingerRequest
import no.nav.su.se.bakover.service.klage.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.klage.NyKlageRequest
import no.nav.su.se.bakover.service.klage.UnderkjennKlageRequest
import no.nav.su.se.bakover.service.klage.VurderKlagevilkårRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser.Brev.kunneIkkeGenerereBrev
import no.nav.su.se.bakover.web.routes.Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeKlage
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkePerson
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeSaksbehandlerEllerAttestant
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeVedtak
import no.nav.su.se.bakover.web.routes.Feilresponser.feilVedHentingAvSaksbehandlerNavn
import no.nav.su.se.bakover.web.routes.Feilresponser.feilVedHentingAvVedtakDato
import no.nav.su.se.bakover.web.routes.Feilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.web.routes.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.tilResultat
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withKlageId
import no.nav.su.se.bakover.web.withSakId
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal const val klagePath = "$sakPath/{sakId}/klager"

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
    authorize(Brukerrolle.Saksbehandler) {
        post(klagePath) {
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
                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson()))
                    }.getOrHandle {
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
                            KunneIkkeOppretteKlage.HarAlleredeEnKlageBehandling -> BadRequest.errorJson(
                                "Finnes allerede en klagebehandling med gitt journalpostId",
                                "finnes_allerede_en_klagebehandling",
                            )
                            is KunneIkkeOppretteKlage.FeilVedHentingAvJournalpost -> it.feil.toErrorJson()
                        }
                    }
                    call.svar(resultat)
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{klageId}/vilkår/vurderinger") {
            data class Body(
                val vedtakId: UUID?,
                val innenforFristen: Svarord?,
                val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
                val erUnderskrevet: Svarord?,
                val begrunnelse: String?,
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
                            begrunnelse = body.begrunnelse,
                        ),
                    ).map {
                        Resultat.json(OK, serialize(it.toJson()))
                    }.getOrHandle {
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

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{klageId}/vilkår/vurderinger/bekreft") {
            call.withKlageId { klageId ->
                val resultat = klageService.bekreftVilkårsvurderinger(
                    klageId = klageId,
                    saksbehandler = call.suUserContext.saksbehandler,
                ).map {
                    Resultat.json(OK, serialize(it.toJson()))
                }.getOrHandle {
                    return@getOrHandle when (it) {
                        KunneIkkeBekrefteKlagesteg.FantIkkeKlage -> fantIkkeKlage
                        is KunneIkkeBekrefteKlagesteg.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                    }
                }
                call.svar(resultat)
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{klageId}/avvist/fritekstTilBrev") {
            data class Body(val fritekst: String)
            call.withKlageId { klageId ->
                call.withBody<Body> { body ->
                    klageService.leggTilAvvistFritekstTilBrev(
                        klageId = klageId,
                        saksbehandler = call.suUserContext.saksbehandler,
                        fritekst = body.fritekst,
                    ).map {
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

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        post("$klagePath/{klageId}/brevutkast") {
            call.withKlageId { klageId ->
                klageService.brevutkast(
                    klageId = klageId,
                    saksbehandler = call.suUserContext.saksbehandler,
                ).fold(
                    ifLeft = {
                        call.svar(it.toErrorJson())
                    },
                    ifRight = {
                        call.respondBytes(it, ContentType.Application.Pdf)
                    },
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{klageId}/vurderinger") {

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
                        Resultat.json(OK, serialize(vurdertKlage.toJson()))
                    }.getOrHandle { error ->
                        error.tilResultat()
                    }
                    call.svar(resultat)
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{klageId}/vurderinger/bekreft") {
            call.withKlageId { klageId ->
                klageService.bekreftVurderinger(
                    klageId = klageId,
                    saksbehandler = call.suUserContext.saksbehandler,
                ).map {
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

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{klageId}/tilAttestering") {
            call.withKlageId { klageId ->
                klageService.sendTilAttestering(klageId, call.suUserContext.saksbehandler).map {
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                }.mapLeft {
                    call.svar(
                        when (it) {
                            KunneIkkeSendeTilAttestering.FantIkkeKlage -> fantIkkeKlage
                            is KunneIkkeSendeTilAttestering.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                            KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
                        },
                    )
                }
            }
        }
    }

    authorize(Brukerrolle.Attestant) {
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
        post("$klagePath/{klageId}/underkjenn") {
            call.withKlageId { klageId ->
                call.withBody<Body> { body ->
                    body.toRequest(klageId, call.suUserContext.attestant).map {
                        klageService.underkjenn(it).map { vurdertKlage ->
                            call.svar(Resultat.json(OK, serialize(vurdertKlage.toJson())))
                        }.mapLeft { error ->
                            call.svar(
                                when (error) {
                                    KunneIkkeUnderkjenne.FantIkkeKlage -> fantIkkeKlage
                                    is KunneIkkeUnderkjenne.UgyldigTilstand -> ugyldigTilstand(error.fra, error.til)
                                    KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
                                    KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
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

    authorize(Brukerrolle.Attestant) {
        post("$klagePath/{klageId}/oversend") {
            call.withKlageId { klageId ->
                klageService.oversend(
                    klageId = klageId,
                    attestant = NavIdentBruker.Attestant(
                        call.suUserContext.navIdent,
                    ),
                ).map {
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                }.mapLeft {
                    call.svar(
                        when (it) {
                            KunneIkkeOversendeKlage.FantIkkeKlage -> fantIkkeKlage
                            is KunneIkkeOversendeKlage.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                            KunneIkkeOversendeKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                            is KunneIkkeOversendeKlage.KunneIkkeLageBrev -> it.feil.toErrorJson()
                            KunneIkkeOversendeKlage.FantIkkeJournalpostIdKnyttetTilVedtaket -> InternalServerError.errorJson(
                                "Fant ikke journalpost-id knyttet til vedtaket. Utviklingsteamet ønsker og bli informert dersom dette oppstår.",
                                "fant_ikke_journalpostid_knyttet_til_vedtaket",
                            )
                            KunneIkkeOversendeKlage.KunneIkkeOversendeTilKlageinstans -> InternalServerError.errorJson(
                                "Kunne ikke oversende til klageinstans",
                                "kunne_ikke_oversende_til_klageinstans",
                            )
                            is KunneIkkeOversendeKlage.KunneIkkeLageBrevRequest -> it.feil.toErrorJson()
                        },
                    )
                }
            }
        }
    }

    authorize(Brukerrolle.Attestant) {
        post("$klagePath/{klageId}/iverksett(AvvistKlage)") {
            call.withKlageId { klageId ->
                klageService.iverksettAvvistKlage(
                    klageId = klageId,
                    attestant = NavIdentBruker.Attestant(
                        call.suUserContext.navIdent,
                    ),
                ).map {
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                }.mapLeft {
                    when (it) {
                        KunneIkkeIverksetteAvvistKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                        KunneIkkeIverksetteAvvistKlage.FantIkkeKlage -> fantIkkeKlage
                        is KunneIkkeIverksetteAvvistKlage.KunneIkkeLageBrev -> it.feil.toErrorJson()
                        is KunneIkkeIverksetteAvvistKlage.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                        KunneIkkeIverksetteAvvistKlage.FeilVedLagringAvDokumentOgKlage -> InternalServerError.errorJson(
                            "Feil ved lagrinng av brev/klagen",
                            "feil_ved_lagring_av_brev_og_klage",
                        )
                        is KunneIkkeIverksetteAvvistKlage.KunneIkkeLageBrevRequest -> it.feil.toErrorJson()
                    }
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        data class Body(val begrunnelse: String)
        post("$klagePath/{klageId}/avslutt") {
            call.withKlageId { klageId ->
                call.withBody<Body> { body ->
                    klageService.avslutt(
                        klageId = klageId,
                        saksbehandler = call.suUserContext.saksbehandler,
                        begrunnelse = body.begrunnelse,
                    ).map {
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
private fun KunneIkkeLageBrevForKlage.toErrorJson(): Resultat {
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

private fun KunneIkkeLageBrevRequest.toErrorJson(): Resultat {
    return when (this) {
        is KunneIkkeLageBrevRequest.FeilVedHentingAvPerson -> this.personFeil.tilResultat()
        is KunneIkkeLageBrevRequest.FeilVedHentingAvSaksbehandlernavn -> feilVedHentingAvSaksbehandlerNavn
        KunneIkkeLageBrevRequest.FeilVedHentingAvVedtakDato,
        -> feilVedHentingAvVedtakDato
        is KunneIkkeLageBrevRequest.UgyldigTilstand -> BadRequest.errorJson(
            "Kan ikke gå fra tilstanden ${fra.simpleName}",
            "ugyldig_tilstand",
        )
    }
}

private fun KunneIkkeLageBrevutkast.toErrorJson(): Resultat {
    return when (this) {
        KunneIkkeLageBrevutkast.FantIkkeKlage -> fantIkkeKlage
        is KunneIkkeLageBrevutkast.FeilVedBrevRequest -> this.feil.toErrorJson()
        is KunneIkkeLageBrevutkast.GenereringAvBrevFeilet -> this.feil.toErrorJson()
    }
}

private fun KunneIkkeHenteJournalpost.toErrorJson(): Resultat {
    return when (this) {
        KunneIkkeHenteJournalpost.FantIkkeJournalpost -> BadRequest.errorJson(
            "Fant ikke journalpost",
            "fant_ikke_journalpost",
        )
        KunneIkkeHenteJournalpost.IkkeTilgang -> Unauthorized.errorJson(
            "Ikke tilgang til Journalpost",
            "ikke_tilgang_til_journalpost",
        )
        KunneIkkeHenteJournalpost.TekniskFeil -> InternalServerError.errorJson(
            "Teknisk feil ved henting av journalpost",
            "teknisk_feil_ved_henting_av_journalpost",
        )
        KunneIkkeHenteJournalpost.Ukjent -> InternalServerError.errorJson(
            "Ukjent feil ved henting av journalpost",
            "ukjent_feil_ved_henting_av_journalpost",
        )
        KunneIkkeHenteJournalpost.UgyldigInput -> BadRequest.errorJson(
            "Ugyldig journalpostId",
            "ugyldig_journalpostId",
        )
        KunneIkkeHenteJournalpost.JournalpostIkkeKnyttetTilSak -> BadRequest.errorJson(
            "Journalposten er ikke knyttet til saken",
            "journalpost_ikke_knyttet_til_sak",
        )
        KunneIkkeHenteJournalpost.JournalpostTemaErIkkeSUP -> BadRequest.errorJson(
            "Journalpost temaet er ikke SUP",
            "journalpost_tema_er_ikke_sup",
        )
        KunneIkkeHenteJournalpost.JournalpostenErIkkeFerdigstilt -> BadRequest.errorJson(
            "Journalposten er ikke ferdigstilt",
            "journalpost_er_ikke_ferdigstilt",
        )
        KunneIkkeHenteJournalpost.JournalpostenErIkkeEtInnkommendeDokument -> BadRequest.errorJson(
            "Journalposten er ikke et innkommende dokument",
            "journalpost_er_ikke_et_innkommende_dokument",
        )
    }
}
