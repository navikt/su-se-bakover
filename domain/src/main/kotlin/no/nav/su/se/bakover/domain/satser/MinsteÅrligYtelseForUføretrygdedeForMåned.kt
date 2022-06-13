package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.periode.Måned
import java.math.BigDecimal
import java.time.LocalDate

data class MinsteÅrligYtelseForUføretrygdedeForMåned(
    val faktor: Faktor,
    val satsKategori: Satskategori,
    val ikrafttredelse: LocalDate,
    val virkningstidspunkt: LocalDate,
    val måned: Måned
) {
    val faktorSomBigDecimal: BigDecimal = faktor.toBigDecimal()
}
