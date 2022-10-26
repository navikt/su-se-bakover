package no.nav.su.se.bakover.domain.revurdering.opprett

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import java.util.UUID

data class OpprettRevurderingCommand(
    val sakId: UUID,
    val periode: Periode,
    private val årsak: String,
    private val begrunnelse: String,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val informasjonSomRevurderes: List<Revurderingsteg>,
) {
    val revurderingsårsak: Either<Revurderingsårsak.UgyldigRevurderingsårsak, Revurderingsårsak> by lazy {
        Revurderingsårsak.tryCreate(
            årsak = årsak,
            begrunnelse = begrunnelse,
        ).flatMap {
            if (it.årsak == Revurderingsårsak.Årsak.MIGRERT) {
                Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak.left()
            } else {
                it.right()
            }
        }
    }
}
