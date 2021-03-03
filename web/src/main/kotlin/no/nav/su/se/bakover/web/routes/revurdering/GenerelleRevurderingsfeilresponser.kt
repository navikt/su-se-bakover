package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.common.periode.Periode.UgyldigPeriode
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import kotlin.reflect.KClass

internal object GenerelleRevurderingsfeilresponser {
    val fantIkkeSak = NotFound.errorJson(
        "Fant ikke sak",
        "fant_ikke_sak",
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

    fun ugyldigPeriode(ugyldigPeriode: UgyldigPeriode): Resultat {
        return BadRequest.errorJson(
            ugyldigPeriode.toString(),
            "ugyldig_periode"
        )
    }

    fun ugyldigTilstand(fra: KClass<out Revurdering>, til: KClass<out Revurdering>): Resultat {
        return BadRequest.errorJson(
            "Kan ikke gå fra tilstanden ${fra.simpleName} til tilstanden ${til.simpleName}",
            "ugyldig_periode"
        )
    }
}
