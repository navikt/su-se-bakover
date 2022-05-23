package no.nav.su.se.bakover.domain.satser

import arrow.core.Nel
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpFactory
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import no.nav.su.se.bakover.domain.grunnbeløp.Grunnbeløpsendring
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate

private val log = LoggerFactory.getLogger(SatsFactoryForSupplerendeStønad::class.java)

class SatsFactoryForSupplerendeStønad(
    private val clock: Clock,
    private val grunnbeløpsendringer: List<Grunnbeløpsendring> = listOf(
        Grunnbeløpsendring(1.mai(2005), 1.mai(2005), 60699),
        Grunnbeløpsendring(1.mai(2006), 1.mai(2006), 62892),
        Grunnbeløpsendring(1.mai(2007), 1.mai(2007), 66812),
        Grunnbeløpsendring(1.mai(2008), 1.mai(2008), 70256),
        Grunnbeløpsendring(1.mai(2009), 1.mai(2009), 72881),
        Grunnbeløpsendring(1.mai(2010), 1.mai(2010), 75641),
        Grunnbeløpsendring(1.mai(2011), 1.mai(2011), 79216),
        Grunnbeløpsendring(1.mai(2012), 1.mai(2012), 82122),
        Grunnbeløpsendring(1.mai(2013), 1.mai(2013), 85245),
        Grunnbeløpsendring(1.mai(2014), 1.mai(2014), 88370),
        Grunnbeløpsendring(1.mai(2015), 1.mai(2015), 90068),
        Grunnbeløpsendring(1.mai(2016), 1.mai(2016), 92576),
        Grunnbeløpsendring(1.mai(2017), 1.mai(2017), 93634),
        Grunnbeløpsendring(1.mai(2018), 1.mai(2018), 96883),
        Grunnbeløpsendring(1.mai(2019), 1.mai(2019), 99858),
        Grunnbeløpsendring(1.mai(2020), 1.mai(2020), 101351),
        Grunnbeløpsendring(1.mai(2021), 1.mai(2021), 106399),
        Grunnbeløpsendring(1.mai(2022), 20.mai(2022), 111477),
    ),
) : SatsFactory {
    private val grunnbeløpFactory: GrunnbeløpFactory = GrunnbeløpFactory(
        clock = clock,
        grunnbeløpsendringer = grunnbeløpsendringer,
    )

    private val minsteÅrligYtelseForUføretrygdede: MinsteÅrligYtelseForUføretrygdedeFactory =
        MinsteÅrligYtelseForUføretrygdedeFactory.createFromFaktorer(
            ordinær = listOf(
                1.januar(2015) to Faktor(2.28),
            ),
            høy = listOf(
                1.januar(2015) to Faktor(2.48),
            ),
        )

    override val formuegrenserFactory: FormuegrenserFactory = FormuegrenserFactory.createFromGrunnbeløp(
        Nel.fromListUnsafe(grunnbeløpFactory.alle()),
    )

    private val uføreOrdiær: FullSupplerendeStønadFactory.Ordinær = FullSupplerendeStønadFactory.Ordinær.Ufør(
        grunnbeløpFactory,
        minsteÅrligYtelseForUføretrygdede,
    )

    private val uføreHøy: FullSupplerendeStønadFactory.Høy = FullSupplerendeStønadFactory.Høy.Ufør(
        grunnbeløpFactory,
        minsteÅrligYtelseForUføretrygdede,
    )
    private val cache = CachedSatsfactoryForSupplerendeStønad(
        mutableMapOf(grunnbeløpsendringer.gjeldende(LocalDate.now(clock)).ikrafttredelse to this),
    )

    private fun factoryForSatskategori(satskategori: Satskategori): FullSupplerendeStønadFactory {
        return when (satskategori) {
            Satskategori.ORDINÆR -> uføreOrdiær
            Satskategori.HØY -> uføreHøy
        }
    }

    override fun forSatskategori(måned: Måned, satskategori: Satskategori): FullSupplerendeStønadForMåned {
        return factoryForSatskategori(satskategori).forMåned(måned)
    }

    /**
     * Instansierer en ny factory med en klokke som representerer nåtid [påDato].
     * Legger til eventuelle nye factories for samme klokke i en [cache], slik at påfølgende operasjoner
     * ikke blir like kostbare mtp minneforbruk.
     */
    override fun gjeldende(påDato: LocalDate): SatsFactory {
        return cache.getOrAdd(grunnbeløpsendringer.gjeldende(påDato).ikrafttredelse)
    }

    override fun høy(måned: Måned): FullSupplerendeStønadForMåned {
        return factoryForSatskategori(Satskategori.HØY).forMåned(måned)
    }

    override fun ordinær(måned: Måned): FullSupplerendeStønadForMåned {
        return factoryForSatskategori(Satskategori.ORDINÆR).forMåned(måned)
    }

    override fun grunnbeløp(måned: Måned): GrunnbeløpForMåned {
        return grunnbeløpFactory.forMåned(måned)
    }

    private class CachedSatsfactoryForSupplerendeStønad(
        private val cache: MutableMap<LocalDate, SatsFactoryForSupplerendeStønad> = mutableMapOf(),
    ) {
        fun getOrAdd(ikrafttredelse: LocalDate): SatsFactoryForSupplerendeStønad {
            return cache.getOrPut(ikrafttredelse) { SatsFactoryForSupplerendeStønad(ikrafttredelse.fixedClock()) }
        }
    }

    private fun List<Grunnbeløpsendring>.gjeldende(påDato: LocalDate): Grunnbeløpsendring {
        return sortedBy { it.ikrafttredelse }.last { it.ikrafttredelse <= påDato }
    }
}
