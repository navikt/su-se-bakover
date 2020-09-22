package no.nav.su.se.bakover.domain.oppdrag

import java.time.LocalDate

data class Utbetalingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: Int
)
