package no.nav.su.se.bakover.domain.oppdrag.simulering

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

sealed interface SimulerUtbetalingRequest {
    val simuleringsperiode: Periode
    val utbetaling: Utbetaling.UtbetalingForSimulering
}

data class SimulerUtbetalingForPeriode(
    override val simuleringsperiode: Periode,
    override val utbetaling: Utbetaling.UtbetalingForSimulering,
) : SimulerUtbetalingRequest
