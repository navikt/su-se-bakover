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
        override val toProsentAvHøyForMåned: ToProsentAvHøyForMåned.Uføre,
    ) : Comparable<FullSupplerendeStønadForMåned>,
        FullSupplerendeStønadForMåned {

        override val sats: BeregnSats = BeregnSats.Uføre.create(grunnbeløp, minsteÅrligYtelseForUføretrygdede)
        override val satsPerÅr: BigDecimal = sats.sats
        override val satsForMåned: BigDecimal = sats.satsMåned

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
        override val toProsentAvHøyForMåned: ToProsentAvHøyForMåned.Alder,
    ) : Comparable<FullSupplerendeStønadForMåned>,
        FullSupplerendeStønadForMåned {

        override val sats: BeregnSats.Alder = BeregnSats.Alder.create(garantipensjonForMåned)
        override val satsPerÅr: BigDecimal = sats.sats
        override val satsForMåned: BigDecimal = sats.satsMåned

        override val satsForMånedAvrundet: Int = satsForMåned.avrund()
        override val satsForMånedAsDouble: Double = satsForMåned.toDouble()

        init {
            require(satsForMåned >= BigDecimal.ZERO)
            require(toProsentAvHøyForMåned.verdi >= BigDecimal.ZERO)
            require(måned == garantipensjonForMåned.måned)
        }

        override val ikrafttredelse: LocalDate = garantipensjonForMåned.ikrafttredelse

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
                val satsMåned = sats.divide(12.toBigDecimal(), MathContext.DECIMAL128)
                return Uføre(
                    sats = sats,
                    satsMåned = satsMåned,
                    benyttetRegel = Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED.benyttRegelspesifisering(
                        verdi = "sats: $sats, satsMåned: $satsMåned",
                        avhengigeRegler = listOf(
                            grunnbeløp.benyttetRegel,
                            minsteÅrligYtelseForUføretrygdede.benyttetRegel,
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
                val satsMåned = sats.divide(12.toBigDecimal(), MathContext.DECIMAL128)
                return Alder(
                    sats = sats,
                    satsMåned = satsMåned,
                    benyttetRegel = Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED.benyttRegelspesifisering(
                        verdi = "sats: $sats, satsMåned: $satsMåned",
                        avhengigeRegler = listOf(
                            garantipensjonForMåned.benyttetRegel,
                        ),
                    ),
                )
            }
        }
    }
}

sealed class ToProsentAvHøyForMåned : RegelspesifisertBeregning {
    abstract val verdi: BigDecimal
    abstract override val benyttetRegel: Regelspesifisering.Beregning

    data class Uføre(
        override val verdi: BigDecimal,
        override val benyttetRegel: Regelspesifisering.Beregning,
    ) : ToProsentAvHøyForMåned() {
        companion object {
            fun create(grunnbeløp: GrunnbeløpForMåned, faktor: MinsteÅrligYtelseForUføretrygdedeForMåned): Uføre {
                val verdi = grunnbeløp.grunnbeløpPerÅr.toBigDecimal()
                    .multiply(faktor.faktorSomBigDecimal)
                    .multiply(TO_PROSENT)
                    .divide(MÅNEDER_PER_ÅR, MathContext.DECIMAL128)
                return Uføre(
                    verdi = verdi,
                    benyttetRegel = Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE.benyttRegelspesifisering(
                        verdi = verdi.toString(),
                        listOf(
                            faktor.benyttetRegel,
                            grunnbeløp.benyttetRegel,
                        ),
                    ),
                )
            }
        }
    }

    data class Alder(
        override val verdi: BigDecimal,
        override val benyttetRegel: Regelspesifisering.Beregning,
    ) : ToProsentAvHøyForMåned() {
        companion object {
            fun create(garantipensjonPerÅr: GarantipensjonForMåned): Alder {
                val verdi = garantipensjonPerÅr.garantipensjonPerÅr.toBigDecimal()
                    .multiply(TO_PROSENT)
                    .divide(MÅNEDER_PER_ÅR, MathContext.DECIMAL128)
                return Alder(
                    verdi = verdi,
                    benyttetRegel = Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_ALDER.benyttRegelspesifisering(
                        verdi = verdi.toString(),
                        listOf(
                            garantipensjonPerÅr.benyttetRegel,
                        ),
                    ),
                )
            }
        }
    }

    companion object {
        private val TO_PROSENT = BigDecimal("0.02")
        private val MÅNEDER_PER_ÅR = BigDecimal("12")
    }
}
