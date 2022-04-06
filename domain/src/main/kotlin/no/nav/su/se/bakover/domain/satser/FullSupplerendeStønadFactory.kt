package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.common.periode.periode
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpFactory
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

// TODO(satsfactory_alder) jah: Vi har ikke satser før 2015 (kun G), denne skal egentlig være 2006-01-01
val supplerendeStønadAlderFlyktningIkrafttredelse: LocalDate = LocalDate.of(2015, 1, 1)

sealed class FullSupplerendeStønadFactory {
    protected abstract val satskategori: Satskategori
    protected abstract val grunnbeløpFactory: GrunnbeløpFactory
    protected abstract val minsteÅrligYtelseForUføretrygdedeFactory: MinsteÅrligYtelseForUføretrygdedeFactory

    private val månedsperioder: Map<Månedsperiode, FullSupplerendeStønadForMåned> by lazy {
        grunnbeløpFactory.månedsperioder.entries
            .filter {
                it.key.inneholder(supplerendeStønadAlderFlyktningIkrafttredelse) || it.key.starterEtter(
                    supplerendeStønadAlderFlyktningIkrafttredelse,
                )
            }.associate { x: Map.Entry<Månedsperiode, GrunnbeløpForMåned> ->
                val minsteÅrligYtelseForUføretrygdede = minsteÅrligYtelseForUføretrygdedeFactory.forMåned(
                    x.key,
                    satskategori,
                )
                val minsteÅrligYtelseForUføretrygdedeHøy = minsteÅrligYtelseForUføretrygdedeFactory.forMåned(
                    x.key,
                    Satskategori.HØY,
                )
                val grunnbeløp = grunnbeløpFactory.forMåned(x.key)
                Pair(
                    x.key,
                    FullSupplerendeStønadForMåned(
                        måned = x.key,
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

    sealed class Ordinær : FullSupplerendeStønadFactory() {
        override val satskategori: Satskategori
            get() = Satskategori.ORDINÆR

        /**
         * https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/minste-arlig-ytelse-for-uforetrygdede_kap
         */
        data class Ufør(
            override val grunnbeløpFactory: GrunnbeløpFactory,
            override val minsteÅrligYtelseForUføretrygdedeFactory: MinsteÅrligYtelseForUføretrygdedeFactory,
        ) : Ordinær()
    }

    sealed class Høy : FullSupplerendeStønadFactory() {

        override val satskategori: Satskategori
            get() = Satskategori.HØY

        /**
         * https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/minste-arlig-ytelse-for-uforetrygdede_kap
         */
        data class Ufør(
            override val grunnbeløpFactory: GrunnbeløpFactory,
            override val minsteÅrligYtelseForUføretrygdedeFactory: MinsteÅrligYtelseForUføretrygdedeFactory,
        ) : Høy()
    }

    fun forMånedsperiode(måned: Månedsperiode): FullSupplerendeStønadForMåned {
        return månedsperioder[måned]
            ?: throw IllegalStateException("Kan ikke avgjøre full supplerende stønad for måned: $måned. Vi har bare data for perioden: ${månedsperioder.periode()}")
    }
}
