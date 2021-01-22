package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

enum class RevurderingsStatus {
    OPPRETTET,
    BEREGNET,
    SIMULERT,
    TIL_ATTESTERING
}

sealed class Revurdering : Saksbehandling() {
    abstract val id: UUID
    abstract val status: RevurderingsStatus
    abstract val opprettet: Tidspunkt
    abstract val tilRevurdering: Behandling
    abstract val saksbehandler: Saksbehandler
    open fun beregn(beregningsgrunnlag: Beregningsgrunnlag): BeregnetRevurdering = BeregnetRevurdering(
        tilRevurdering = tilRevurdering,
        id = id,
        opprettet = Tidspunkt.now(),
        beregning = tilRevurdering.behandlingsinformasjon().bosituasjon!!.getBeregningStrategy()
            .beregn(beregningsgrunnlag),
        saksbehandler = saksbehandler
    )
}

data class OpprettetRevurdering(
    override val id: UUID = UUID.randomUUID(),
    override val status: RevurderingsStatus = RevurderingsStatus.OPPRETTET,
    override val opprettet: Tidspunkt = Tidspunkt.now(),
    override val tilRevurdering: Behandling,
    override val saksbehandler: Saksbehandler,
) : Revurdering()

data class BeregnetRevurdering(
    override val id: UUID,
    override val status: RevurderingsStatus = RevurderingsStatus.BEREGNET,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Behandling,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning
) : Revurdering() {
    fun toSimulert(simulering: Simulering) = SimulertRevurdering(
        id = id,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        beregning = beregning,
        simulering = simulering,
        saksbehandler = saksbehandler
    )
}

data class SimulertRevurdering(
    override val id: UUID,
    override val status: RevurderingsStatus = RevurderingsStatus.SIMULERT,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Behandling,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering
) : Revurdering()

data class TilAttesteringRevurdering(
    override val id: UUID,
    override val status: RevurderingsStatus = RevurderingsStatus.TIL_ATTESTERING,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Behandling,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering,
    val oppgaveId: OppgaveId
) : Revurdering()
