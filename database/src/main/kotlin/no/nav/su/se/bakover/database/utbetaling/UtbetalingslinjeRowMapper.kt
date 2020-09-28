package no.nav.su.se.bakover.database.utbetaling

import kotliquery.Row
import no.nav.su.se.bakover.database.uuid30
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje

internal fun Row.toUtbetalingslinje(): Utbetalingslinje {
    return Utbetalingslinje(
        id = uuid30("id"),
        fom = localDate("fom"),
        tom = localDate("tom"),
        opprettet = instant("opprettet"),
        forrigeUtbetalingslinjeId = stringOrNull("forrigeUtbetalingslinjeId")?.let { uuid30("forrigeUtbetalingslinjeId") },
        beløp = int("beløp")
    )
}
