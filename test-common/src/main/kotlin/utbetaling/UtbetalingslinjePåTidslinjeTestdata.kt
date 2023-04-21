package no.nav.su.se.bakover.test.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingslinjePåTidslinje

fun utbetalingslinjePåTidslinjeNy(
    periode: Periode = år(2021),
    beløp: Int = 100,
    kopiertFraId: UUID30 = UUID30.randomUUID(),
) = UtbetalingslinjePåTidslinje.Ny(
    periode = periode,
    beløp = beløp,
    kopiertFraId = kopiertFraId,
)

fun utbetalingslinjePåTidslinjeOpphør(
    periode: Periode = år(2021),
    beløp: Int = 100,
    kopiertFraId: UUID30 = UUID30.randomUUID(),
) = UtbetalingslinjePåTidslinje.Opphør(
    periode = periode,
    beløp = beløp,
    kopiertFraId = kopiertFraId,
)

fun utbetalingslinjePåTidslinjeStans(
    periode: Periode = år(2021),
    beløp: Int = 100,
    kopiertFraId: UUID30 = UUID30.randomUUID(),
) = UtbetalingslinjePåTidslinje.Stans(
    periode = periode,
    beløp = beløp,
    kopiertFraId = kopiertFraId,
)

fun utbetalingslinjePåTidslinjeReaktivering(
    periode: Periode = år(2021),
    beløp: Int = 100,
    kopiertFraId: UUID30 = UUID30.randomUUID(),
) = UtbetalingslinjePåTidslinje.Reaktivering(
    periode = periode,
    beløp = beløp,
    kopiertFraId = kopiertFraId,
)
