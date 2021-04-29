package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode
import no.nav.su.se.bakover.domain.revurdering.Revurdering
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

    fun ugyldigPeriode(ugyldigPeriode: UgyldigPeriode): Resultat {
        return BadRequest.errorJson(
            ugyldigPeriode.toString(),
            "ugyldig_periode"
        )
    }

    fun ugyldigTilstand(fra: KClass<out Revurdering>, til: KClass<out Revurdering>): Resultat {
        return BadRequest.errorJson(
            "Kan ikke gå fra tilstanden ${fra.simpleName} til tilstanden ${til.simpleName}",
            "ugyldig_tilstand"
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
