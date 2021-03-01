package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson

internal object GenerelleRevurderingsfeilresponser {
    val fantIkkeSak = HttpStatusCode.NotFound.errorJson(
        "Fant ikke sak",
        "fant_ikke_sak",
    )
    val fantIkkeAktørId = HttpStatusCode.NotFound.errorJson(
        "Fant ikke aktør id",
        "fant_ikke_aktør_id",
    )
    val kunneIkkeOppretteOppgave = HttpStatusCode.InternalServerError.errorJson(
        "Kunne ikke opprette oppgave",
        "kunne_ikke_opprette_oppgave",
    )

    fun ugyldigPeriode(ugyldigPeriode: KunneIkkeOppretteRevurdering.UgyldigPeriode): Resultat {
        return HttpStatusCode.BadRequest.errorJson(
            ugyldigPeriode.subError.toString(),
            "ugyldig_periode"
        )
    }
}
