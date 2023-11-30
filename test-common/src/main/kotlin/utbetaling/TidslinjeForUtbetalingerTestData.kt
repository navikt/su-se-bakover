package no.nav.su.se.bakover.test.utbetaling

import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.Utbetalingslinje

fun List<Utbetalingslinje>.tidslinje(): TidslinjeForUtbetalinger {
    return TidslinjeForUtbetalinger.fra(
        Utbetalinger(
            oversendtUtbetalingUtenKvittering(
                utbetalingslinjer = this.toNonEmptyList(),
            ),
        ),
    )!!
}
