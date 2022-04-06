package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.periode.Månedsperiode
import java.math.BigDecimal
import java.time.LocalDate

data class MinsteÅrligYtelseForUføretrygdedeForMåned(
    val faktor: Faktor,
    val satsKategori: Satskategori,
    val ikrafttredelse: LocalDate,
    val måned: Månedsperiode
) {
    val faktorSomBigDecimal: BigDecimal = faktor.toBigDecimal()
}
