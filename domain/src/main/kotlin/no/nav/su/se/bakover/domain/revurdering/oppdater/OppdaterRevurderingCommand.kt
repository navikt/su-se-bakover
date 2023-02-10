package no.nav.su.se.bakover.domain.revurdering.oppdater

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import java.util.UUID

data class OppdaterRevurderingCommand(
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
