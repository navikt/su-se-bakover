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
import kotlin.reflect.KClass

internal object Revurderingsfeilresponser {
    val fantIkkeSak = NotFound.errorJson(
        "Fant ikke sak",
        "fant_ikke_sak",
    )
    val fantIkkePerson = NotFound.errorJson(
        "Fant ikke person",
        "fant_ikke_person",
    )
    val fantIkkeAktørId = NotFound.errorJson(
        "Fant ikke aktør id",
        "fant_ikke_aktør_id",
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

    val formueListeKanIkkeVæreTom = BadRequest.errorJson(
        "Formueliste kan ikke være tom",
        "formueliste_kan_ikke_være_tom",
    )

    val ikkeLovMedOverlappendePerioder = BadRequest.errorJson(
        "Ikke lov med overlappende perioder",
        "ikke_lov_med_overlappende_perioder",
    )

    fun ugyldigPeriode(ugyldigPeriode: UgyldigPeriode): Resultat {
        return BadRequest.errorJson(
            ugyldigPeriode.toString(),
            "ugyldig_periode",
        )
    }

    fun ugyldigTilstand(fra: KClass<*>, til: KClass<*>): Resultat {
        return BadRequest.errorJson(
            "Kan ikke gå fra tilstanden ${fra.simpleName} til tilstanden ${til.simpleName}",
            "ugyldig_tilstand",
        )
    }

    fun KunneIkkeForhåndsvarsle.tilResultat() = when (this) {
        is KunneIkkeForhåndsvarsle.AlleredeForhåndsvarslet -> HttpStatusCode.Conflict.errorJson(
            "Allerede forhåndsvarslet",
            "allerede_forhåndsvarslet",
        )
        is KunneIkkeForhåndsvarsle.FantIkkeAktørId -> fantIkkeAktørId
        is KunneIkkeForhåndsvarsle.FantIkkePerson -> fantIkkePerson
        is KunneIkkeForhåndsvarsle.KunneIkkeDistribuere -> InternalServerError.errorJson(
            "Kunne ikke distribuere brev",
            "kunne_ikke_distribuere_brev",
        )
        is KunneIkkeForhåndsvarsle.KunneIkkeJournalføre -> InternalServerError.errorJson(
            "Kunne ikke journalføre brev",
            "kunne_ikke_journalføre_brev",
        )
        is KunneIkkeForhåndsvarsle.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
        is KunneIkkeForhåndsvarsle.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeForhåndsvarsle.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
        is KunneIkkeForhåndsvarsle.Attestering -> this.subError.tilResultat()
        is KunneIkkeForhåndsvarsle.KunneIkkeHenteNavnForSaksbehandler -> InternalServerError.errorJson(
            "Kunne ikke hente navn for saksbehandler eller attestant",
            "navneoppslag_feilet",
        )
    }

    fun KunneIkkeLageBrevutkastForRevurdering.tilResultat(): Resultat {
        return when (this) {
            is KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast -> InternalServerError.errorJson(
                "Kunne ikke lage brevutkast",
                "kunne_ikke_lage_brevutkast",
            )
            KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson -> InternalServerError.errorJson(
                "Fant ikke person",
                "fant_ikke_person",
            )
            KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> InternalServerError.errorJson(
                "Kunne ikke hente navn for saksbehandler eller attestant",
                "navneoppslag_feilet",
            )
        }
    }
}
