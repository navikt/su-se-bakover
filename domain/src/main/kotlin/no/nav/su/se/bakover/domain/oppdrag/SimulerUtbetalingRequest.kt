package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
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
        val utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger
    }

    interface OpphørRequest : SimulerUtbetalingRequest {
        val opphørsdato: LocalDate
    }

    interface StansRequest : SimulerUtbetalingRequest {
        val stansdato: LocalDate
    }

    interface GjenopptakRequest : SimulerUtbetalingRequest {
        val sak: Sak
    }

    data class NyUtbetaling(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker,
        override val beregning: Beregning,
        override val uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        override val utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger,
    ) : NyUtbetalingRequest

    data class Opphør(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker,
        override val opphørsdato: LocalDate,
    ) : OpphørRequest

    data class Stans(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker,
        override val stansdato: LocalDate,
    ) : StansRequest

    data class Gjenopptak(
        override val saksbehandler: NavIdentBruker,
        override val sak: Sak,
    ) : GjenopptakRequest {
        override val sakId = sak.id
    }
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

    data class Stans(
        private val request: SimulerUtbetalingRequest.Stans,
        override val simulering: Simulering,
    ) : UtbetalRequest,
        SimulerUtbetalingRequest.StansRequest by request

    data class Gjenopptak(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker,
        override val simulering: Simulering,
    ) : UtbetalRequest
}
