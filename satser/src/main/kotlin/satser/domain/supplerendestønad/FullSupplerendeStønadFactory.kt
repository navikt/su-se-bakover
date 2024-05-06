package satser.domain.supplerendestønad

import arrow.core.NonEmptyList
import grunnbeløp.domain.GrunnbeløpFactory
import no.nav.su.se.bakover.common.domain.Knekkpunkt
import no.nav.su.se.bakover.common.domain.Knekkpunkt.Companion.compareTo
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.erSammenhengendeSortertOgUtenDuplikater
import satser.domain.Satskategori
import satser.domain.garantipensjon.GarantipensjonFactory
import satser.domain.minsteårligytelseforuføretrygdede.MinsteÅrligYtelseForUføretrygdedeFactory
import java.math.BigDecimal
import java.math.MathContext

sealed interface FullSupplerendeStønadFactory {
    val satskategori: Satskategori
    val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned>

    /** Knekkpunktet til factorien */
    val knekkpunkt: Knekkpunkt

    /** Inclusive */
    val tidligsteTilgjengeligeMåned: Måned

    /** Inclusive */
    val senesteTilgjengeligeMåned: Måned

    val måneder: NonEmptyList<Måned>

    companion object {
        protected val TO_PROSENT = BigDecimal("0.02")
        protected val MÅNEDER_PER_ÅR = BigDecimal("12")
    }

    fun månedTilFullSupplerendeStønadForUføre(
        grunnbeløpFactory: GrunnbeløpFactory,
        minsteÅrligYtelseForUføretrygdedeFactory: MinsteÅrligYtelseForUføretrygdedeFactory,
        måneder: List<Måned>,
    ): Map<Måned, FullSupplerendeStønadForMåned.Uføre> {
        require(grunnbeløpFactory.knekkpunkt == minsteÅrligYtelseForUføretrygdedeFactory.knekkpunkt)
        return måneder.associateWith { måned ->
            val grunnbeløp = grunnbeløpFactory.forMåned(måned)
            val minsteÅrligYtelseForUføretrygdede = minsteÅrligYtelseForUføretrygdedeFactory.forMåned(
                måned,
                satskategori,
            )
            val minsteÅrligYtelseForUføretrygdedeHøy = minsteÅrligYtelseForUføretrygdedeFactory.forMåned(
                måned,
                Satskategori.HØY,
            )
            FullSupplerendeStønadForMåned.Uføre(
                måned = måned,
                satskategori = satskategori,
                grunnbeløp = grunnbeløp,
                minsteÅrligYtelseForUføretrygdede = minsteÅrligYtelseForUføretrygdede,
                toProsentAvHøyForMåned = grunnbeløp.grunnbeløpPerÅr.toBigDecimal()
                    .multiply(minsteÅrligYtelseForUføretrygdedeHøy.faktorSomBigDecimal)
                    .multiply(TO_PROSENT)
                    .divide(MÅNEDER_PER_ÅR, MathContext.DECIMAL128),
            )
        }
    }

    fun satskategori(): Satskategori {
        return satskategori
    }

    fun forMåned(måned: Måned): FullSupplerendeStønadForMåned

    sealed interface Ordinær : FullSupplerendeStønadFactory {
        override val satskategori get() = Satskategori.ORDINÆR

        /**
         * https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/minste-arlig-ytelse-for-uforetrygdede_kap
         */
        data class Ufør(
            val grunnbeløpFactory: GrunnbeløpFactory,
            val minsteÅrligYtelseForUføretrygdedeFactory: MinsteÅrligYtelseForUføretrygdedeFactory,
            override val knekkpunkt: Knekkpunkt,
            override val tidligsteTilgjengeligeMåned: Måned,
            override val måneder: NonEmptyList<Måned>,
        ) : Ordinær {

            override val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned.Uføre>
                get() = månedTilFullSupplerendeStønadForUføre(
                    grunnbeløpFactory = grunnbeløpFactory,
                    minsteÅrligYtelseForUføretrygdedeFactory = minsteÅrligYtelseForUføretrygdedeFactory,
                    måneder = måneder,
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
            override val måneder: NonEmptyList<Måned>,
        ) : Ordinær {
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

    sealed interface Høy : FullSupplerendeStønadFactory {

        override val satskategori get() = Satskategori.HØY

        /**
         * https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/minste-arlig-ytelse-for-uforetrygdede_kap
         */
        data class Ufør(
            val grunnbeløpFactory: GrunnbeløpFactory,
            val minsteÅrligYtelseForUføretrygdedeFactory: MinsteÅrligYtelseForUføretrygdedeFactory,
            override val knekkpunkt: Knekkpunkt,
            override val tidligsteTilgjengeligeMåned: Måned,
            override val måneder: NonEmptyList<Måned>,
        ) : Høy {
            override val månedTilFullSupplerendeStønad: Map<Måned, FullSupplerendeStønadForMåned.Uføre>
                get() = månedTilFullSupplerendeStønadForUføre(
                    grunnbeløpFactory = grunnbeløpFactory,
                    minsteÅrligYtelseForUføretrygdedeFactory = minsteÅrligYtelseForUføretrygdedeFactory,
                    måneder = måneder,
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
            override val måneder: NonEmptyList<Måned>,
        ) : Høy {
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
