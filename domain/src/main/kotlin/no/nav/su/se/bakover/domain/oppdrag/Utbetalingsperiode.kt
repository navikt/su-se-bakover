package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import java.time.LocalDate

data class Utbetalingsperiode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val beløp: Int,
    val uføregrad: Uføregrad,
)
