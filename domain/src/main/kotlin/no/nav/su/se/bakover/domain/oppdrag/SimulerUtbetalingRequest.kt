package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.LocalDate
import java.util.UUID

sealed interface SimulerUtbetalingRequest {
    val sakId: UUID
    val saksbehandler: NavIdentBruker

    interface NyUtbetalingRequest : SimulerUtbetalingRequest {
        val beregning: Beregning
        val uføregrunnlag: List<Grunnlag.Uføregrunnlag>
    }

    interface OpphørRequest : SimulerUtbetalingRequest {
        val opphørsdato: LocalDate
    }

    data class NyUtbetaling(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker,
        override val beregning: Beregning,
        override val uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    ) : NyUtbetalingRequest

    data class Opphør(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker,
        override val opphørsdato: LocalDate,
    ) : OpphørRequest
}

sealed interface UtbetalRequest : SimulerUtbetalingRequest {
    val simulering: Simulering

    data class NyUtbetaling(
        private val request: SimulerUtbetalingRequest.NyUtbetalingRequest,
        override val simulering: Simulering,
    ) : UtbetalRequest,
        SimulerUtbetalingRequest.NyUtbetalingRequest by request

    data class Opphør(
        private val request: SimulerUtbetalingRequest.OpphørRequest,
        override val simulering: Simulering,
    ) : UtbetalRequest,
        SimulerUtbetalingRequest.OpphørRequest by request
}
