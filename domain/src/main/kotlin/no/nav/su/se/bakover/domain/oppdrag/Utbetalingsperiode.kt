package no.nav.su.se.bakover.domain.oppdrag

import java.time.LocalDate

data class Utbetalingsperiode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val beløp: Double
)
