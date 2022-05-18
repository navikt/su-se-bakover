package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.avrund
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

data class FullSupplerendeStønadForMåned(
    val måned: Måned,
    val satskategori: Satskategori,
    val grunnbeløp: GrunnbeløpForMåned,
    val minsteÅrligYtelseForUføretrygdede: MinsteÅrligYtelseForUføretrygdedeForMåned,
    val toProsentAvHøyForMåned: BigDecimal,
) : Comparable<FullSupplerendeStønadForMåned> {

    val satsPerÅr: BigDecimal = grunnbeløp.grunnbeløpPerÅr.toBigDecimal().multiply(minsteÅrligYtelseForUføretrygdede.faktorSomBigDecimal)

    val satsForMåned: BigDecimal = satsPerÅr.divide(12.toBigDecimal(), MathContext.DECIMAL128)
    val satsForMånedAvrundet: Int = satsForMåned.avrund()
    val satsForMånedAsDouble: Double = satsForMåned.toDouble()

    init {
        require(satsForMåned >= BigDecimal.ZERO)
        require(toProsentAvHøyForMåned >= BigDecimal.ZERO)
    }

    /** Nyeste ikraftredelsen av grunnbeløpet og minsteÅrligYtelseForUføretrygdede som gjelder for denne måneden. */
    val ikrafttredelse: LocalDate = maxOf(grunnbeløp.ikrafttredelse, minsteÅrligYtelseForUføretrygdede.ikrafttredelse)

    val toProsentAvHøyForMånedAsDouble = toProsentAvHøyForMåned.toDouble()

    val fraOgMed: LocalDate = måned.fraOgMed
    val tilOgMed: LocalDate = måned.tilOgMed
    val periode: Måned = måned

    override fun compareTo(other: FullSupplerendeStønadForMåned) = this.måned.compareTo(other.måned)
}
