package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode
import no.nav.su.se.bakover.service.revurdering.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkePerson
import no.nav.su.se.bakover.web.routes.Feilresponser.ugyldigTilstand

internal object Revurderingsfeilresponser {
    val fantIkkeSak = NotFound.errorJson(
        "Fant ikke sak",
        "fant_ikke_sak",
    )

    val kunneIkkeOppretteOppgave = InternalServerError.errorJson(
        "Kunne ikke opprette oppgave",
        "kunne_ikke_opprette_oppgave",
    )
    val fantIkkeRevurdering = NotFound.errorJson(
        "Fant ikke revurdering",
        "fant_ikke_revurdering",
    )
    val manglerBeslutningPåForhåndsvarsel = BadRequest.errorJson(
        "Mangler beslutning på forhåndsvarsel",
        "mangler_beslutning_på_forhåndsvarsel",
    )

    val måVelgeInformasjonSomRevurderes = BadRequest.errorJson(
        "Må velge minst en ting som skal revurderes",
        "må_velge_informasjon_som_revurderes",
    )

    val feilutbetalingStøttesIkke = InternalServerError.errorJson(
        "Feilutbetalinger støttes ikke",
        "feilutbetalinger_støttes_ikke",
    )

    val fantIngenVedtakSomKanRevurderes = NotFound.errorJson(
        "Fant ingen vedtak som kan revurderes for angitt periode",
        "ingenting_å_revurdere_i_perioden",
    )

    val tidslinjeForVedtakErIkkeKontinuerlig = InternalServerError.errorJson(
        "Mangler systemstøtte for revurdering av perioder med hull i tidslinjen for vedtak",
        "tidslinje_for_vedtak_ikke_kontinuerlig",
    )

    val bosituasjonMedFlerePerioderMåRevurderes = BadRequest.errorJson(
        "Bosituasjon må revurderes siden det finnes bosituasjonsperioder",
        "bosituasjon_med_flere_perioder_må_revurderes",
    )
    val epsInntektMedFlereBosituasjonsperioderMåRevurderes = BadRequest.errorJson(
        "Inntekt må revurderes siden det finnes EPS inntekt og flere bosituasjonsperioder",
        "eps_inntekt_med_flere_perioder_må_revurderes",
    )

    val formueSomFørerTilOpphørMåRevurderes = BadRequest.errorJson(
        "Formue som fører til opphør må revurderes",
        "formue_som_fører_til_opphør_må_revurderes",
    )

    val brevFantIkkePerson = InternalServerError.errorJson(
        "Fant ikke person",
        "fant_ikke_person",
    )

    val brevNavneoppslagSaksbehandlerAttesttantFeilet = InternalServerError.errorJson(
        "Kunne ikke hente navn for saksbehandler eller attestant",
        "navneoppslag_feilet",
    )

    val brevFantIkkeGjeldendeUtbetaling = InternalServerError.errorJson(
        "Kunne ikke hente gjeldende utbetaling",
        "kunne_ikke_hente_gjeldende_utbetaling",
    )

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
        is KunneIkkeForhåndsvarsle.KunneIkkeHenteNavnForSaksbehandler -> InternalServerError.errorJson(
            "Kunne ikke hente navn for saksbehandler eller attestant",
            "navneoppslag_feilet",
        )
        KunneIkkeForhåndsvarsle.KunneIkkeGenerereDokument -> InternalServerError.errorJson(
            "Feil ved generering av dokument",
            "feil_ved_generering_av_dokument",
        )
    }

    fun KunneIkkeLageBrevutkastForRevurdering.tilResultat(): Resultat {
        return when (this) {
            is KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast -> InternalServerError.errorJson(
                "Kunne ikke lage brevutkast",
                "kunne_ikke_lage_brevutkast",
            )
            KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson -> brevFantIkkePerson
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> brevNavneoppslagSaksbehandlerAttesttantFeilet
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeFinneGjeldendeUtbetaling -> brevFantIkkeGjeldendeUtbetaling
        }
    }
}
