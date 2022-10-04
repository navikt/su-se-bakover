package no.nav.su.se.bakover.service.revurdering

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import java.util.UUID

data class OppdaterRevurderingRequest(
    val revurderingId: UUID,
    val periode: Periode,
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
