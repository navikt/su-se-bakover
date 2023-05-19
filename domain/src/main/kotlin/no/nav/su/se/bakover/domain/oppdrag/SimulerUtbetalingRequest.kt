package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

sealed interface SimulerUtbetalingRequest {
    val sakId: UUID
    val saksbehandler: NavIdentBruker

    sealed interface NyUtbetalingRequest : SimulerUtbetalingRequest {
        val beregning: Beregning
        val utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger
    }

    interface OpphørRequest : SimulerUtbetalingRequest {
        val opphørsperiode: Periode
    }

    sealed class NyUtbetaling : NyUtbetalingRequest {
        data class Uføre(
            override val sakId: UUID,
            override val saksbehandler: NavIdentBruker,
            override val beregning: Beregning,
            override val utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            val uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        ) : NyUtbetaling()

        data class Alder(
            override val sakId: UUID,
            override val saksbehandler: NavIdentBruker,
            override val beregning: Beregning,
            override val utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        ) : NyUtbetaling()
    }

    data class Opphør(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker,
        override val opphørsperiode: Periode,
    ) : OpphørRequest
}

sealed interface UtbetalRequest : SimulerUtbetalingRequest {
    val simulering: Simulering

    data class NyUtbetaling(
        val request: SimulerUtbetalingRequest.NyUtbetaling,
        override val simulering: Simulering,
    ) : UtbetalRequest,
        SimulerUtbetalingRequest.NyUtbetalingRequest by request

    data class Opphør(
        private val request: SimulerUtbetalingRequest.OpphørRequest,
        override val simulering: Simulering,
    ) : UtbetalRequest,
        SimulerUtbetalingRequest.OpphørRequest by request
}
