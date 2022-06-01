package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.periode
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpFactory
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.Month

// TODO(satsfactory_alder) jah: I lov om supplerende stønad ble satsen for alder endret fra minste pensjonsnivå til garantipensjon.
//  Vi må legge inn minste pensjonsnivå og ta høyde for det før vi skal revurdere tilbake til før 2021-01-01.
//  På grunn av testene må vi sette sperren til 2020
val supplerendeStønadAlderFlyktningIkrafttredelse: LocalDate = LocalDate.of(2020, Month.JANUARY, 1)

sealed class FullSupplerendeStønadFactory {
    protected abstract val satskategori: Satskategori
    protected abstract val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned>

    protected fun månedTilFullSupplerendeStønadForUføre(
        grunnbeløpFactory: GrunnbeløpFactory,
        minsteÅrligYtelseForUføretrygdedeFactory: MinsteÅrligYtelseForUføretrygdedeFactory,
    ): Map<Måned, FullSupplerendeStønadForMåned.Uføre> {
        require(grunnbeløpFactory.påDato == minsteÅrligYtelseForUføretrygdedeFactory.påDato)
        return grunnbeløpFactory.alleGrunnbeløp(supplerendeStønadAlderFlyktningIkrafttredelse)
            .associate { grunnbeløp ->
                val minsteÅrligYtelseForUføretrygdede = minsteÅrligYtelseForUføretrygdedeFactory.forMåned(
                    grunnbeløp.måned,
                    satskategori,
                )
                val minsteÅrligYtelseForUføretrygdedeHøy = minsteÅrligYtelseForUføretrygdedeFactory.forMåned(
                    grunnbeløp.måned,
                    Satskategori.HØY,
                )
                Pair(
                    grunnbeløp.måned,
                    FullSupplerendeStønadForMåned.Uføre(
                        måned = grunnbeløp.måned,
                        satskategori = satskategori,
                        grunnbeløp = grunnbeløp,
                        minsteÅrligYtelseForUføretrygdede = minsteÅrligYtelseForUføretrygdede,
                        toProsentAvHøyForMåned = BigDecimal(grunnbeløp.grunnbeløpPerÅr)
                            .multiply(minsteÅrligYtelseForUføretrygdedeHøy.faktorSomBigDecimal)
                            .multiply(BigDecimal("0.02"))
                            .divide(12.toBigDecimal(), MathContext.DECIMAL128),
                    ),
                )
            }
    }

    fun satskategori(): Satskategori {
        return satskategori
    }

    abstract fun forMåned(måned: Måned): FullSupplerendeStønadForMåned

    sealed class Ordinær : FullSupplerendeStønadFactory() {
        override val satskategori = Satskategori.ORDINÆR

        /**
         * https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/minste-arlig-ytelse-for-uforetrygdede_kap
         */
        data class Ufør(
            val grunnbeløpFactory: GrunnbeløpFactory,
            val minsteÅrligYtelseForUføretrygdedeFactory: MinsteÅrligYtelseForUføretrygdedeFactory,
        ) : Ordinær() {
            override val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned.Uføre>
                get() = månedTilFullSupplerendeStønadForUføre(
                    grunnbeløpFactory = grunnbeløpFactory,
                    minsteÅrligYtelseForUføretrygdedeFactory = minsteÅrligYtelseForUføretrygdedeFactory,
                )

            override fun forMåned(måned: Måned): FullSupplerendeStønadForMåned.Uføre {
                return månedTilFullSupplerendeStønad[måned]
                    ?: throw IllegalStateException("Kan ikke avgjøre full supplerende stønad for måned: $måned. Vi har bare data for perioden: ${månedTilFullSupplerendeStønad.periode()}")
            }
        }

        data class Alder(
            val garantipensjonFactory: GarantipensjonFactory,
        ) : Ordinær() {
            override val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned.Alder>
                get() = garantipensjonFactory.ordinær.mapValues {
                    FullSupplerendeStønadForMåned.Alder(
                        måned = it.value.måned,
                        satskategori = Satskategori.ORDINÆR,
                        garantipensjonForMåned = it.value,
                        toProsentAvHøyForMåned = garantipensjonFactory.høy[it.value.måned]!!.garantipensjonPerÅr
                            .toBigDecimal()
                            .divide(12.toBigDecimal(), MathContext.DECIMAL128),
                    )
                }

            override fun forMåned(måned: Måned): FullSupplerendeStønadForMåned.Alder {
                return månedTilFullSupplerendeStønad[måned]
                    ?: throw IllegalStateException("Kan ikke avgjøre full supplerende stønad for måned: $måned. Vi har bare data for perioden: ${månedTilFullSupplerendeStønad.periode()}")
            }
        }
    }

    sealed class Høy : FullSupplerendeStønadFactory() {

        override val satskategori = Satskategori.HØY

        /**
         * https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/minste-arlig-ytelse-for-uforetrygdede_kap
         */
        data class Ufør(
            val grunnbeløpFactory: GrunnbeløpFactory,
            val minsteÅrligYtelseForUføretrygdedeFactory: MinsteÅrligYtelseForUføretrygdedeFactory,
        ) : Høy() {
            override val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned.Uføre>
                get() = månedTilFullSupplerendeStønadForUføre(
                    grunnbeløpFactory = grunnbeløpFactory,
                    minsteÅrligYtelseForUføretrygdedeFactory = minsteÅrligYtelseForUføretrygdedeFactory,
                )

            override fun forMåned(måned: Måned): FullSupplerendeStønadForMåned.Uføre {
                return månedTilFullSupplerendeStønad[måned]
                    ?: throw IllegalStateException("Kan ikke avgjøre full supplerende stønad for måned: $måned. Vi har bare data for perioden: ${månedTilFullSupplerendeStønad.periode()}")
            }
        }

        data class Alder(
            val garantipensjonFactory: GarantipensjonFactory,
        ) : Høy() {
            override val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned.Alder>
                get() = garantipensjonFactory.høy.mapValues {
                    FullSupplerendeStønadForMåned.Alder(
                        måned = it.value.måned,
                        satskategori = Satskategori.HØY,
                        garantipensjonForMåned = it.value,
                        toProsentAvHøyForMåned = it.value.garantipensjonPerÅr
                            .toBigDecimal()
                            .divide(12.toBigDecimal(), MathContext.DECIMAL128),
                    )
                }

            override fun forMåned(måned: Måned): FullSupplerendeStønadForMåned.Alder {
                return månedTilFullSupplerendeStønad[måned]
                    ?: throw IllegalStateException("Kan ikke avgjøre full supplerende stønad for måned: $måned. Vi har bare data for perioden: ${månedTilFullSupplerendeStønad.periode()}")
            }
        }
    }
}
