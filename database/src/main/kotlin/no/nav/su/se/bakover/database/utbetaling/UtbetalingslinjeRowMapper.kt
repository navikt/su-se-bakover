package no.nav.su.se.bakover.database.utbetaling

import kotliquery.Row
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid30
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje

internal fun Row.toUtbetalingslinje(): Utbetalingslinje {
    return Utbetalingslinje(
        id = uuid30("id"),
        fraOgMed = localDate("fom"),
        tilOgMed = localDate("tom"),
        opprettet = tidspunkt("opprettet"),
        forrigeUtbetalingslinjeId = stringOrNull("forrigeUtbetalingslinjeId")?.let { uuid30("forrigeUtbetalingslinjeId") },
        beløp = int("beløp")
    )
}
