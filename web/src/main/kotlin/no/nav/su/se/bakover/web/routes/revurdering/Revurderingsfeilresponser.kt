package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.routes.Feilresponser.Brev.kunneIkkeLageBrevutkast
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkePerson
import no.nav.su.se.bakover.web.routes.Feilresponser.feilVedGenereringAvDokument
import no.nav.su.se.bakover.web.routes.Feilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.web.routes.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.Brev.fantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.Brev.navneoppslagSaksbehandlerAttesttantFeilet

internal object Revurderingsfeilresponser {
    val fantIkkeSak = NotFound.errorJson(
        "Fant ikke sak",
        "fant_ikke_sak",
    )

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

    object OpprettelseOgOppdateringAvRevurdering {
        val måVelgeInformasjonSomRevurderes = BadRequest.errorJson(
            "Må velge minst en ting som skal revurderes",
            "må_velge_informasjon_som_revurderes",
        )
        val tidslinjeForVedtakErIkkeKontinuerlig = InternalServerError.errorJson(
            "Mangler systemstøtte for revurdering av perioder med hull i tidslinjen for vedtak",
            "tidslinje_for_vedtak_ikke_kontinuerlig",
        )
        val begrunnelseKanIkkeVæreTom = BadRequest.errorJson(
            "Begrunnelse kan ikke være tom",
            "begrunnelse_kan_ikke_være_tom",
        )
        val ugyldigÅrsak = BadRequest.errorJson(
            "Ugyldig årsak, må være en av: ${Revurderingsårsak.Årsak.values()}",
            "ugyldig_årsak",
        )
        val bosituasjonMedFlerePerioderMåRevurderes = BadRequest.errorJson(
            "Bosituasjon og inntekt må revurderes siden det finnes flere bosituasjonsperioder",
            "bosituasjon_med_flere_perioder_må_revurderes",
        )
        val formueSomFørerTilOpphørMåRevurderes = BadRequest.errorJson(
            "Formue som fører til opphør må revurderes",
            "formue_som_fører_til_opphør_må_revurderes",
        )

        val epsFormueMedFlereBosituasjonsperioderMåRevurderes = BadRequest.errorJson(
            "Formue må revurderes siden det finnes EPS formue og flere bosituasjonsperioder",
            "eps_formue_med_flere_perioder_må_revurderes",
        )

        val revurderingsperiodeInneholderAvkortingPgaUtenlandsopphold = InternalServerError.errorJson(
            "Revurderingsperioden inneholder akortinger pga utenlandsopphold, dette støttes ikke",
            "revurderingsperiode_inneholder_avkorting_utenlandsopphold"
        )
    }

    object Brev {
        val navneoppslagSaksbehandlerAttesttantFeilet = InternalServerError.errorJson(
            "Kunne ikke hente navn for saksbehandler eller attestant",
            "navneoppslag_feilet",
        )

        val fantIkkeGjeldendeUtbetaling = InternalServerError.errorJson(
            "Kunne ikke hente gjeldende utbetaling",
            "kunne_ikke_hente_gjeldende_utbetaling",
        )
    }

    fun ugyldigPeriode(ugyldigPeriode: UgyldigPeriode): Resultat {
        return BadRequest.errorJson(
            ugyldigPeriode.toString(),
            "ugyldig_periode",
        )
    }

    fun KunneIkkeForhåndsvarsle.tilResultat() = when (this) {
        is KunneIkkeForhåndsvarsle.AlleredeForhåndsvarslet -> HttpStatusCode.Conflict.errorJson(
            "Allerede forhåndsvarslet",
            "allerede_forhåndsvarslet",
        )
        is KunneIkkeForhåndsvarsle.FantIkkePerson -> fantIkkePerson
        is KunneIkkeForhåndsvarsle.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
        is KunneIkkeForhåndsvarsle.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeForhåndsvarsle.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
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
}
