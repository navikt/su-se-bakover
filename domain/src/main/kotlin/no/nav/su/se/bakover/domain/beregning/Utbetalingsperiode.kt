package no.nav.su.se.bakover.domain.beregning

import java.time.LocalDate

data class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: Int
)
