package satser.domain.supplerendestønad

import grunnbeløp.domain.GrunnbeløpForMåned
import no.nav.su.se.bakover.common.domain.extensions.avrund
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.tid.periode.Måned
import satser.domain.Satskategori
import satser.domain.garantipensjon.GarantipensjonForMåned
import satser.domain.minsteårligytelseforuføretrygdede.MinsteÅrligYtelseForUføretrygdedeForMåned
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
    val toProsentAvHøyForMåned: ToProsentAvHøyForMåned
    val sats: BeregnSats
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
        override val toProsentAvHøyForMåned: ToProsentAvHøyForMåned,
    ) : Comparable<FullSupplerendeStønadForMåned>,
        FullSupplerendeStønadForMåned {

        override val sats: BeregnSats = BeregnSats.Uføre.create(grunnbeløp, minsteÅrligYtelseForUføretrygdede)
        override val satsPerÅr: BigDecimal = sats.sats
        override val satsForMåned: BigDecimal = sats.satsMåned

        // TODO bjg - fjern/erstatt kun test og brevvsining..
        override val satsForMånedAvrundet: Int = satsForMåned.avrund()
        override val satsForMånedAsDouble: Double = satsForMåned.toDouble()

        init {
            require(satsForMåned >= BigDecimal.ZERO)
            require(toProsentAvHøyForMåned.verdi >= BigDecimal.ZERO)
            require(måned == minsteÅrligYtelseForUføretrygdede.måned)
            require(måned == grunnbeløp.måned)
        }

        /** Nyeste ikraftredelsen av grunnbeløpet og minsteÅrligYtelseForUføretrygdede som gjelder for denne måneden. */
        override val ikrafttredelse: LocalDate =
            maxOf(grunnbeløp.ikrafttredelse, minsteÅrligYtelseForUføretrygdede.ikrafttredelse)

        override val toProsentAvHøyForMånedAsDouble = toProsentAvHøyForMåned.verdi.toDouble()

        override val periode: Måned = måned

        override fun compareTo(other: FullSupplerendeStønadForMåned) = this.måned.compareTo(other.måned)
    }

    data class Alder(
        override val måned: Måned,
        override val satskategori: Satskategori,
        val garantipensjonForMåned: GarantipensjonForMåned,
        override val toProsentAvHøyForMåned: ToProsentAvHøyForMåned,
    ) : Comparable<FullSupplerendeStønadForMåned>,
        FullSupplerendeStønadForMåned {

        override val sats: BeregnSats.Alder = BeregnSats.Alder.create(garantipensjonForMåned)
        override val satsPerÅr: BigDecimal = sats.sats
        override val satsForMåned: BigDecimal = sats.satsMåned

        // TODO bjg - fjern / erstatt
        override val satsForMånedAvrundet: Int = satsForMåned.avrund()
        override val satsForMånedAsDouble: Double = satsForMåned.toDouble()

        init {
            require(satsForMåned >= BigDecimal.ZERO)
            require(toProsentAvHøyForMåned.verdi >= BigDecimal.ZERO)
            require(måned == garantipensjonForMåned.måned)
        }

        override val ikrafttredelse: LocalDate = garantipensjonForMåned.ikrafttredelse

        // TODO bjg erstatt
        override val toProsentAvHøyForMånedAsDouble = toProsentAvHøyForMåned.verdi.toDouble()

        override val periode: Måned = måned

        override fun compareTo(other: FullSupplerendeStønadForMåned) = this.måned.compareTo(other.måned)
    }
}

sealed class BeregnSats : RegelspesifisertBeregning {
    abstract val sats: BigDecimal
    abstract val satsMåned: BigDecimal

    data class Uføre(
        override val sats: BigDecimal,
        override val satsMåned: BigDecimal,
        override val benyttetRegel: Regelspesifisering,
    ) : BeregnSats() {

        companion object {
            fun create(
                grunnbeløp: GrunnbeløpForMåned,
                minsteÅrligYtelseForUføretrygdede: MinsteÅrligYtelseForUføretrygdedeForMåned,
            ): Uføre {
                val sats = grunnbeløp.grunnbeløpPerÅr
                    .toBigDecimal()
                    .multiply(minsteÅrligYtelseForUføretrygdede.faktorSomBigDecimal)
                return Uføre(
                    sats = sats,
                    satsMåned = sats.divide(12.toBigDecimal(), MathContext.DECIMAL128),
                    benyttetRegel = Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED.benyttRegelspesifisering(
                        avhengigeRegler = listOf(
                            // TODO bjg - riktig å ha denne her? strengt talt ikke her den utledes..
                            Regelspesifiseringer.REGEL_UFØRE_FAKTOR.benyttRegelspesifisering(),
                        ),
                    ),
                )
            }
        }
    }

    data class Alder(
        override val sats: BigDecimal,
        override val satsMåned: BigDecimal,
        override val benyttetRegel: Regelspesifisering,
    ) : BeregnSats() {
        companion object {
            fun create(garantipensjonForMåned: GarantipensjonForMåned): Alder {
                val sats = garantipensjonForMåned.garantipensjonPerÅr.toBigDecimal()
                return Alder(
                    sats = sats,
                    satsMåned = sats.divide(12.toBigDecimal(), MathContext.DECIMAL128),
                    benyttetRegel = Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED.benyttRegelspesifisering(),
                )
            }
        }
    }
}

sealed class ToProsentAvHøyForMåned : RegelspesifisertBeregning {
    abstract val verdi: BigDecimal

    data class Uføre(
        override val verdi: BigDecimal,
        override val benyttetRegel: Regelspesifisering,
    ) : ToProsentAvHøyForMåned() {
        companion object {
            fun create(grunnbeløp: GrunnbeløpForMåned, faktorSomBigDecimal: BigDecimal): Uføre {
                return Uføre(
                    verdi = grunnbeløp.grunnbeløpPerÅr.toBigDecimal()
                        .multiply(faktorSomBigDecimal)
                        .multiply(TO_PROSENT)
                        .divide(MÅNEDER_PER_ÅR, MathContext.DECIMAL128),
                    benyttetRegel = Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE.benyttRegelspesifisering(),
                )
            }
        }
    }

    data class Alder(
        override val verdi: BigDecimal,
        override val benyttetRegel: Regelspesifisering,
    ) : ToProsentAvHøyForMåned() {
        companion object {
            fun create(garantipensjonPerÅr: BigDecimal): Uføre {
                return Uføre(
                    verdi = garantipensjonPerÅr
                        .multiply(TO_PROSENT)
                        .divide(MÅNEDER_PER_ÅR, MathContext.DECIMAL128),
                    benyttetRegel = Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_ALDER.benyttRegelspesifisering(),
                )
            }
        }
    }

    companion object {
        private val TO_PROSENT = BigDecimal("0.02")
        private val MÅNEDER_PER_ÅR = BigDecimal("12")
    }
}
