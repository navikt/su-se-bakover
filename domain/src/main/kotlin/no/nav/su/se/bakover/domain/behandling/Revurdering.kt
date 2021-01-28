package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

sealed class Revurdering {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val tilRevurdering: Behandling
    abstract val periode: Periode
    abstract val saksbehandler: Saksbehandler
    open fun beregn(beregningsgrunnlag: Beregningsgrunnlag): BeregnetRevurdering = BeregnetRevurdering(
        tilRevurdering = tilRevurdering,
        id = id,
        periode = periode,
        opprettet = Tidspunkt.now(),
        beregning = tilRevurdering.behandlingsinformasjon().bosituasjon!!.getBeregningStrategy()
            .beregn(beregningsgrunnlag),
        saksbehandler = saksbehandler
    )
}

data class OpprettetRevurdering(
    override val id: UUID = UUID.randomUUID(),
    override val periode: Periode,
    override val opprettet: Tidspunkt = Tidspunkt.now(),
    override val tilRevurdering: Behandling,
    override val saksbehandler: Saksbehandler,
) : Revurdering()

data class BeregnetRevurdering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Behandling,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning
) : Revurdering() {
    fun toSimulert(simulering: Simulering) = SimulertRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        beregning = beregning,
        simulering = simulering,
        saksbehandler = saksbehandler
    )
}

data class SimulertRevurdering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Behandling,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering
) : Revurdering()

data class RevurderingTilAttestering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Behandling,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering,
    val oppgaveId: OppgaveId
) : Revurdering()
