package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

sealed class Revurdering : Saksbehandling() {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val tilRevurdering: Behandling
    open fun beregn(beregningsgrunnlag: Beregningsgrunnlag): BeregnetRevurdering = BeregnetRevurdering(
        tilRevurdering = tilRevurdering,
        id = id,
        opprettet = Tidspunkt.now(),
        beregning = tilRevurdering.behandlingsinformasjon().bosituasjon!!.getBeregningStrategy()
            .beregn(beregningsgrunnlag)
    )
}

open class OpprettetRevurdering(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Behandling
) : Revurdering() {
}

open class BeregnetRevurdering(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Behandling,
    open val beregning: Beregning
) : Revurdering() {
    fun toSimulert(simulering: Simulering) = SimulertRevurdering(
        id, opprettet, tilRevurdering, beregning, simulering
    )
}

open class SimulertRevurdering(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Behandling,
    val beregning: Beregning,
    val simulering: Simulering
) : Revurdering() {
}

open class TilAttesteringRevurdering(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Behandling,
    val beregning: Beregning,
    val simulering: Simulering
) : Revurdering() {
    override fun beregn(beregningsgrunnlag: Beregningsgrunnlag) = throw RuntimeException()
}
