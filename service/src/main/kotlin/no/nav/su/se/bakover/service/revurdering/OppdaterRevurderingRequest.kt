package no.nav.su.se.bakover.service.revurdering

import no.nav.su.se.bakover.domain.bruker.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import java.time.LocalDate
import java.util.UUID

data class OppdaterRevurderingRequest(
    val revurderingId: UUID,
    val fraOgMed: LocalDate,
    val årsak: String,
    val begrunnelse: String,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val informasjonSomRevurderes: List<Revurderingsteg>,
) {
    val revurderingsårsak = Revurderingsårsak.tryCreate(
        årsak = årsak,
        begrunnelse = begrunnelse,
    )
}
