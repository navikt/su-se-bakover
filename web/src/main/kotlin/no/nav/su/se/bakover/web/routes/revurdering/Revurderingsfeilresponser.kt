package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.Brev.kunneIkkeLageBrevutkast
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkePerson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.feilVedGenereringAvDokument
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.Periode.UgyldigPeriode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.avkorting.KanIkkeRevurderePgaAvkorting
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.Brev.brevvalgIkkeTillatt
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.Brev.fantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.Brev.navneoppslagSaksbehandlerAttesttantFeilet

internal data object Revurderingsfeilresponser {

    val fantIkkeRevurdering = NotFound.errorJson(
        "Fant ikke revurdering",
        "fant_ikke_revurdering",
    )

    val fantIngenVedtakSomKanRevurderes = NotFound.errorJson(
        "Fant ingen vedtak som kan revurderes for angitt periode",
        "ingenting_å_revurdere_i_perioden",
    )

    val fantIkkePersonEllerSaksbehandlerNavn = BadRequest.errorJson(
        "Fant ikke person eller saksbehandler navn",
        "fant_ikke_person_eller_saksbehandler_navn",
    )

    data object OpprettelseOgOppdateringAvRevurdering {
        val måVelgeInformasjonSomRevurderes = BadRequest.errorJson(
            "Må velge minst en ting som skal revurderes",
            "må_velge_informasjon_som_revurderes",
        )
        val heleRevurderingsperiodenInneholderIkkeVedtak = InternalServerError.errorJson(
            "Sak mangler vedtak for en eller flere måneder i valgt revurderingsperiode!",
            "vedtak_mangler_i_en_eller_flere_måneder_av_revurderingsperiode",
        )
        val begrunnelseKanIkkeVæreTom = BadRequest.errorJson(
            "Begrunnelse kan ikke være tom",
            "begrunnelse_kan_ikke_være_tom",
        )
        val ugyldigÅrsak = BadRequest.errorJson(
            "Ugyldig årsak, må være en av: ${Revurderingsårsak.Årsak.entries}",
            "ugyldig_årsak",
        )
        val formueSomFørerTilOpphørMåRevurderes = BadRequest.errorJson(
            "Formue som fører til opphør må revurderes",
            "formue_som_fører_til_opphør_må_revurderes",
        )
        val utenlandsoppholdSomFørerTilOpphørMåRevurderes = BadRequest.errorJson(
            "Utenlandsopphold som fører til opphør må revurderes",
            "utenlandsopphold_som_fører_til_opphør_må_revurderes",
        )

        val epsFormueMedFlereBosituasjonsperioderMåRevurderes = BadRequest.errorJson(
            "Formue må revurderes siden det finnes EPS formue og flere bosituasjonsperioder",
            "eps_formue_med_flere_perioder_må_revurderes",
        )

        fun uteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(periode: Periode) = InternalServerError.errorJson(
            "Saken har en utestående avkorting som enten må avkortes i ny stønadsperiode eller revurderes i sin helhet. Vennligst inkluder ${periode.fraOgMed}-${periode.tilOgMed} i revurderingsperioden eller avkort i ny stønadsperiode.",
            "utestående_avkorting_må_revurderes_eller_avkortes_i_ny_periode",
        )

        fun pågåendeAvkortingForPeriode(periode: Periode, vedtakId: String) = BadRequest.errorJson(
            "Pågående avkorting for periode:$periode i vedtak:$vedtakId. Hele perioden for opprinnelig avkortingsvarsel og eventuelle fradrag for avkorting må inkluderes, eller behandlingen må deles opp.",
            "pågende_avkorting_for_periode",
        )
    }

    data object Brev {
        val navneoppslagSaksbehandlerAttesttantFeilet = InternalServerError.errorJson(
            "Kunne ikke hente navn for saksbehandler eller attestant",
            "navneoppslag_feilet",
        )

        val fantIkkeGjeldendeUtbetaling = InternalServerError.errorJson(
            "Kunne ikke hente gjeldende utbetaling",
            "kunne_ikke_hente_gjeldende_utbetaling",
        )
        val brevvalgIkkeTillatt = BadRequest.errorJson(
            "Brevvalg ikke tillatt",
            "brevvalg_ikke_tillatt",
        )
        val manglerBrevvalg = BadRequest.errorJson(
            "Mangler brevvalg",
            "mangler_brevvalg",
        )
    }

    fun ugyldigPeriode(ugyldigPeriode: UgyldigPeriode): Resultat {
        return BadRequest.errorJson(
            ugyldigPeriode.toString(),
            "ugyldig_periode",
        )
    }

    fun KunneIkkeForhåndsvarsle.tilResultat() = when (this) {
        is KunneIkkeForhåndsvarsle.FantIkkePerson -> fantIkkePerson
        is KunneIkkeForhåndsvarsle.KunneIkkeOppdatereOppgave -> kunneIkkeOppretteOppgave
        is KunneIkkeForhåndsvarsle.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeForhåndsvarsle.UgyldigTilstand -> BadRequest.errorJson(
            "Ugyldig tilstand",
            "ugyldig_tilstand",
        )

        is KunneIkkeForhåndsvarsle.Attestering -> this.subError.tilResultat()
        is KunneIkkeForhåndsvarsle.KunneIkkeHenteNavnForSaksbehandler -> navneoppslagSaksbehandlerAttesttantFeilet
        KunneIkkeForhåndsvarsle.KunneIkkeGenerereDokument -> feilVedGenereringAvDokument
    }

    fun KunneIkkeLageBrevutkastForRevurdering.tilResultat(): Resultat {
        return when (this) {
            is KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast -> kunneIkkeLageBrevutkast
            KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson -> fantIkkePerson
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> navneoppslagSaksbehandlerAttesttantFeilet
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeFinneGjeldendeUtbetaling -> fantIkkeGjeldendeUtbetaling
            KunneIkkeLageBrevutkastForRevurdering.DetSkalIkkeSendesBrev -> brevvalgIkkeTillatt
            KunneIkkeLageBrevutkastForRevurdering.UgyldigTilstand -> BadRequest.errorJson(
                "Ugyldig tilstand",
                "ugyldig_tilstand",
            )
        }
    }

    fun Revurderingsårsak.UgyldigRevurderingsårsak.tilResultat(): Resultat {
        return when (this) {
            Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigBegrunnelse -> BadRequest.errorJson(
                message = "Ugyldig begrunnelse for revurdering",
                code = "revurderingsårsak_ugyldig_begrunnelse",
            )

            Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak -> BadRequest.errorJson(
                message = "Ugyldig årsak for revurdering",
                code = "revurderingsårsak_ugyldig_årsak",
            )
        }
    }

    internal fun Sak.OpphørtVilkårMåRevurderes.tilResultat(): Resultat {
        return when (this) {
            is Sak.OpphørtVilkårMåRevurderes.FormueSomFørerTilOpphørMåRevurderes -> {
                OpprettelseOgOppdateringAvRevurdering.formueSomFørerTilOpphørMåRevurderes
            }

            is Sak.OpphørtVilkårMåRevurderes.UtenlandsoppholdSomFørerTilOpphørMåRevurderes -> {
                OpprettelseOgOppdateringAvRevurdering.utenlandsoppholdSomFørerTilOpphørMåRevurderes
            }
        }
    }

    internal fun Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.tilResultat(): Resultat {
        return when (this) {
            is Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.FantIngenVedtakSomKanRevurderes -> {
                fantIngenVedtakSomKanRevurderes
            }

            is Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.HeleRevurderingsperiodenInneholderIkkeVedtak -> {
                OpprettelseOgOppdateringAvRevurdering.heleRevurderingsperiodenInneholderIkkeVedtak
            }
        }
    }

    internal fun Sak.KunneIkkeHenteGjeldendeVedtaksdata.tilResultat() = when (this) {
        is Sak.KunneIkkeHenteGjeldendeVedtaksdata.FinnesIngenVedtakSomKanRevurderes -> {
            fantIngenVedtakSomKanRevurderes
        }

        is Sak.KunneIkkeHenteGjeldendeVedtaksdata.UgyldigPeriode -> {
            ugyldigPeriode(this.feil)
        }
    }

    internal fun KanIkkeRevurderePgaAvkorting.tilResultat(): Resultat {
        return when (this) {
            is KanIkkeRevurderePgaAvkorting.PågåendeAvkortingForPeriode -> {
                OpprettelseOgOppdateringAvRevurdering.pågåendeAvkortingForPeriode(
                    periode = periode,
                    vedtakId = vedtakId.toString(),
                )
            }
            is KanIkkeRevurderePgaAvkorting.UteståendeAvkortingMåRevurderesISinHelhet -> {
                OpprettelseOgOppdateringAvRevurdering.uteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(periode)
            }
        }
    }
}
