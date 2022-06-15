package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.avrund
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/**
 * Full supplerende stønad er for mange perioder definert som en kombinasjon av flere satser.
 * Den kan også endre hva slags satstype som ligger til grunn.
 * F.eks. SU Alder gikk fra å bruke minstepensjon til garantipensjon i 2021-01-01
 */
sealed interface FullSupplerendeStønadForMåned {
    val måned: Måned
    val satskategori: Satskategori
    val toProsentAvHøyForMåned: BigDecimal
    val satsPerÅr: BigDecimal
    val satsForMåned: BigDecimal
    val satsForMånedAvrundet: Int
    val satsForMånedAsDouble: Double

    /** Dette vil være den nyeste ikrafttredelsesdatoen basert på de satsene som gjaldt på denne datoen.
     * F.eks. sats for uføre er satt sammen av grunnbeløp og minste årlig ytelse for uføretrygdede den 2021-01-01.
     * Disse vil i teorien kunne ha forskjellige ikrafttredelsedatoen. F.eks. 2020-05-01 og 20220-10-01.
     * Da vil den nyeste av disse datoene være ikrafttredelsen til full supplerende stønad.
     */
    val ikrafttredelse: LocalDate
    val toProsentAvHøyForMånedAsDouble: Double
    val periode: Måned

    data class Uføre(
        override val måned: Måned,
        override val satskategori: Satskategori,
        val grunnbeløp: GrunnbeløpForMåned,
        val minsteÅrligYtelseForUføretrygdede: MinsteÅrligYtelseForUføretrygdedeForMåned,
        override val toProsentAvHøyForMåned: BigDecimal,
    ) : Comparable<FullSupplerendeStønadForMåned>, FullSupplerendeStønadForMåned {

        override val satsPerÅr: BigDecimal =
            grunnbeløp.grunnbeløpPerÅr
                .toBigDecimal()
                .multiply(minsteÅrligYtelseForUføretrygdede.faktorSomBigDecimal)

        override val satsForMåned: BigDecimal = satsPerÅr.divide(12.toBigDecimal(), MathContext.DECIMAL128)
        override val satsForMånedAvrundet: Int = satsForMåned.avrund()
        override val satsForMånedAsDouble: Double = satsForMåned.toDouble()

        init {
            require(satsForMåned >= BigDecimal.ZERO)
            require(toProsentAvHøyForMåned >= BigDecimal.ZERO)
            require(måned == minsteÅrligYtelseForUføretrygdede.måned)
            require(måned == grunnbeløp.måned)
        }

        /** Nyeste ikraftredelsen av grunnbeløpet og minsteÅrligYtelseForUføretrygdede som gjelder for denne måneden. */
        override val ikrafttredelse: LocalDate =
            maxOf(grunnbeløp.ikrafttredelse, minsteÅrligYtelseForUføretrygdede.ikrafttredelse)

        override val toProsentAvHøyForMånedAsDouble = toProsentAvHøyForMåned.toDouble()

        override val periode: Måned = måned

        override fun compareTo(other: FullSupplerendeStønadForMåned) = this.måned.compareTo(other.måned)
    }

    data class Alder(
        override val måned: Måned,
        override val satskategori: Satskategori,
        val garantipensjonForMåned: GarantipensjonForMåned,
        override val toProsentAvHøyForMåned: BigDecimal,
    ) : Comparable<FullSupplerendeStønadForMåned>, FullSupplerendeStønadForMåned {

        override val satsPerÅr: BigDecimal = garantipensjonForMåned.garantipensjonPerÅr.toBigDecimal()

        override val satsForMåned: BigDecimal = satsPerÅr.divide(12.toBigDecimal(), MathContext.DECIMAL128)
        override val satsForMånedAvrundet: Int = satsForMåned.avrund()
        override val satsForMånedAsDouble: Double = satsForMåned.toDouble()

        init {
            require(satsForMåned >= BigDecimal.ZERO)
            require(toProsentAvHøyForMåned >= BigDecimal.ZERO)
            require(måned == garantipensjonForMåned.måned)
        }

        override val ikrafttredelse: LocalDate = garantipensjonForMåned.ikrafttredelse

        override val toProsentAvHøyForMånedAsDouble = toProsentAvHøyForMåned.toDouble()

        override val periode: Måned = måned

        override fun compareTo(other: FullSupplerendeStønadForMåned) = this.måned.compareTo(other.måned)
    }
}
