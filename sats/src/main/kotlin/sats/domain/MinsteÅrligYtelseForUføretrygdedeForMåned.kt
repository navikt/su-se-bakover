package sats.domain

import no.nav.su.se.bakover.common.domain.Faktor
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.math.BigDecimal
import java.time.LocalDate

data class MinsteÅrligYtelseForUføretrygdedeForMåned(
    val faktor: Faktor,
    val satsKategori: Satskategori,
    val ikrafttredelse: LocalDate,
    val virkningstidspunkt: LocalDate,
    val måned: Måned,
) {
    val faktorSomBigDecimal: BigDecimal = faktor.toBigDecimal()
}
