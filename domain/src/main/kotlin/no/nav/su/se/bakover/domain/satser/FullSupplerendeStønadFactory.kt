package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpFactory
import no.nav.su.se.bakover.domain.satser.Knekkpunkt.Companion.compareTo
import java.math.BigDecimal
import java.math.MathContext

sealed class FullSupplerendeStønadFactory {
    protected abstract val satskategori: Satskategori
    protected abstract val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned>

    /** Knekkpunktet til factorien */
    protected abstract val knekkpunkt: Knekkpunkt

    /** Inclusive */
    protected abstract val tidligsteTilgjengeligeMåned: Måned

    /** Inclusive */
    protected abstract val senesteTilgjengeligeMåned: Måned

    companion object {
        protected val TO_PROSENT = BigDecimal("0.02")
        protected val MÅNEDER_PER_ÅR = BigDecimal("12")
    }

    protected fun månedTilFullSupplerendeStønadForUføre(
        grunnbeløpFactory: GrunnbeløpFactory,
        minsteÅrligYtelseForUføretrygdedeFactory: MinsteÅrligYtelseForUføretrygdedeFactory,
    ): Map<Måned, FullSupplerendeStønadForMåned.Uføre> {
        require(grunnbeløpFactory.knekkpunkt == minsteÅrligYtelseForUføretrygdedeFactory.knekkpunkt)
        return grunnbeløpFactory.alleGrunnbeløp(tidligsteTilgjengeligeMåned)
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
                            .multiply(TO_PROSENT)
                            .divide(MÅNEDER_PER_ÅR, MathContext.DECIMAL128),
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
            override val knekkpunkt: Knekkpunkt,
            override val tidligsteTilgjengeligeMåned: Måned,
        ) : Ordinær() {

            override val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned.Uføre>
                get() = månedTilFullSupplerendeStønadForUføre(
                    grunnbeløpFactory = grunnbeløpFactory,
                    minsteÅrligYtelseForUføretrygdedeFactory = minsteÅrligYtelseForUføretrygdedeFactory,
                )

            init {
                require(knekkpunkt == grunnbeløpFactory.knekkpunkt)
                require(knekkpunkt == minsteÅrligYtelseForUføretrygdedeFactory.knekkpunkt)
                require(månedTilFullSupplerendeStønad.values.all { it.ikrafttredelse <= knekkpunkt })
                require(månedTilFullSupplerendeStønad.isNotEmpty())
                require(månedTilFullSupplerendeStønad.erSammenhengendeSortertOgUtenDuplikater())
                require(månedTilFullSupplerendeStønad.keys.first() == tidligsteTilgjengeligeMåned)
            }

            override val senesteTilgjengeligeMåned = månedTilFullSupplerendeStønad.keys.last()

            override fun forMåned(måned: Måned): FullSupplerendeStønadForMåned.Uføre {
                return månedTilFullSupplerendeStønad[måned]
                    ?: if (måned > senesteTilgjengeligeMåned) {
                        månedTilFullSupplerendeStønad[senesteTilgjengeligeMåned]!!.copy(
                            måned = måned,
                            grunnbeløp = grunnbeløpFactory.forMåned(måned),
                            minsteÅrligYtelseForUføretrygdede = minsteÅrligYtelseForUføretrygdedeFactory.forMåned(
                                måned,
                                satskategori,
                            ),
                        )
                    } else {
                        throw IllegalArgumentException(
                            "Har ikke data for etterspurt måned: $måned. Vi har bare data fra og med måned: ${månedTilFullSupplerendeStønad.keys.first()}",
                        )
                    }
            }
        }

        data class Alder(
            val garantipensjonFactory: GarantipensjonFactory,
            override val knekkpunkt: Knekkpunkt,
            override val tidligsteTilgjengeligeMåned: Måned,
        ) : Ordinær() {
            override val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned.Alder>
                get() = garantipensjonFactory.ordinær.mapValues { (måned, garantipensjonForMåned) ->
                    FullSupplerendeStønadForMåned.Alder(
                        måned = måned,
                        satskategori = Satskategori.ORDINÆR,
                        garantipensjonForMåned = garantipensjonForMåned,
                        toProsentAvHøyForMåned = garantipensjonFactory.forMåned(
                            måned,
                            Satskategori.HØY,
                        ).garantipensjonPerÅr
                            .toBigDecimal()
                            .multiply(TO_PROSENT)
                            .divide(MÅNEDER_PER_ÅR, MathContext.DECIMAL128),
                    )
                }

            init {
                require(knekkpunkt == garantipensjonFactory.knekkpunkt)
                require(månedTilFullSupplerendeStønad.values.all { it.ikrafttredelse <= knekkpunkt })
                require(månedTilFullSupplerendeStønad.isNotEmpty())
                require(månedTilFullSupplerendeStønad.erSammenhengendeSortertOgUtenDuplikater())
                require(månedTilFullSupplerendeStønad.keys.first() == tidligsteTilgjengeligeMåned)
            }

            override val senesteTilgjengeligeMåned = månedTilFullSupplerendeStønad.keys.last()

            override fun forMåned(måned: Måned): FullSupplerendeStønadForMåned.Alder {
                return månedTilFullSupplerendeStønad[måned]
                    ?: if (måned > senesteTilgjengeligeMåned) {
                        månedTilFullSupplerendeStønad[senesteTilgjengeligeMåned]!!.copy(
                            måned = måned,
                            garantipensjonForMåned = garantipensjonFactory.forMåned(måned, satskategori),
                        )
                    } else {
                        throw IllegalArgumentException(
                            "Har ikke data for etterspurt måned: $måned. Vi har bare data fra og med måned: ${månedTilFullSupplerendeStønad.keys.first()}",
                        )
                    }
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
            override val knekkpunkt: Knekkpunkt,
            override val tidligsteTilgjengeligeMåned: Måned,
        ) : Høy() {
            override val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned.Uføre>
                get() = månedTilFullSupplerendeStønadForUføre(
                    grunnbeløpFactory = grunnbeløpFactory,
                    minsteÅrligYtelseForUføretrygdedeFactory = minsteÅrligYtelseForUføretrygdedeFactory,
                )

            init {
                require(knekkpunkt == grunnbeløpFactory.knekkpunkt)
                require(knekkpunkt == minsteÅrligYtelseForUføretrygdedeFactory.knekkpunkt)
                require(månedTilFullSupplerendeStønad.values.all { it.ikrafttredelse <= knekkpunkt })
                require(månedTilFullSupplerendeStønad.isNotEmpty())
                require(månedTilFullSupplerendeStønad.erSammenhengendeSortertOgUtenDuplikater())
                require(månedTilFullSupplerendeStønad.keys.first() == tidligsteTilgjengeligeMåned)
            }

            override val senesteTilgjengeligeMåned = månedTilFullSupplerendeStønad.keys.last()

            override fun forMåned(måned: Måned): FullSupplerendeStønadForMåned.Uføre {
                return månedTilFullSupplerendeStønad[måned]
                    ?: if (måned > senesteTilgjengeligeMåned) {
                        månedTilFullSupplerendeStønad[senesteTilgjengeligeMåned]!!.copy(
                            måned = måned,
                            grunnbeløp = grunnbeløpFactory.forMåned(måned),
                            minsteÅrligYtelseForUføretrygdede = minsteÅrligYtelseForUføretrygdedeFactory.forMåned(
                                måned,
                                satskategori,
                            ),
                        )
                    } else {
                        throw IllegalArgumentException(
                            "Har ikke data for etterspurt måned: $måned. Vi har bare data fra og med måned: ${månedTilFullSupplerendeStønad.keys.first()}",
                        )
                    }
            }
        }

        data class Alder(
            val garantipensjonFactory: GarantipensjonFactory,
            override val knekkpunkt: Knekkpunkt,
            override val tidligsteTilgjengeligeMåned: Måned,
        ) : Høy() {
            override val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned.Alder>
                get() = garantipensjonFactory.høy.mapValues { (måned, garantipensjonForMåned) ->
                    FullSupplerendeStønadForMåned.Alder(
                        måned = måned,
                        satskategori = Satskategori.HØY,
                        garantipensjonForMåned = garantipensjonForMåned,
                        toProsentAvHøyForMåned = garantipensjonForMåned.garantipensjonPerÅr.toBigDecimal()
                            .multiply(TO_PROSENT)
                            .divide(MÅNEDER_PER_ÅR, MathContext.DECIMAL128),
                    )
                }

            init {
                require(knekkpunkt == garantipensjonFactory.knekkpunkt)
                require(månedTilFullSupplerendeStønad.values.all { it.ikrafttredelse <= knekkpunkt })
                require(månedTilFullSupplerendeStønad.isNotEmpty())
                require(månedTilFullSupplerendeStønad.erSammenhengendeSortertOgUtenDuplikater())
                require(månedTilFullSupplerendeStønad.keys.first() == tidligsteTilgjengeligeMåned)
            }

            override val senesteTilgjengeligeMåned = månedTilFullSupplerendeStønad.keys.last()

            override fun forMåned(måned: Måned): FullSupplerendeStønadForMåned.Alder {
                return månedTilFullSupplerendeStønad[måned]
                    ?: if (måned > senesteTilgjengeligeMåned) {
                        månedTilFullSupplerendeStønad[senesteTilgjengeligeMåned]!!.copy(
                            måned = måned,
                            garantipensjonForMåned = garantipensjonFactory.forMåned(måned, satskategori),
                        )
                    } else {
                        throw IllegalArgumentException(
                            "Har ikke data for etterspurt måned: $måned. Vi har bare data fra og med måned: ${månedTilFullSupplerendeStønad.keys.first()}",
                        )
                    }
            }
        }
    }
}
