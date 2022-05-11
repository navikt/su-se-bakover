package no.nav.su.se.bakover.domain.satser

import arrow.core.Nel
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpFactory
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SatsFactoryForSupplerendeStønad::class.java)

class SatsFactoryForSupplerendeStønad(
    override val grunnbeløpFactory: GrunnbeløpFactory = GrunnbeløpFactory.createFromGrunnbeløp(
        grunnbeløp = listOf(
            1.mai(2005) to 60699,
            1.mai(2006) to 62892,
            1.mai(2007) to 66812,
            1.mai(2008) to 70256,
            1.mai(2009) to 72881,
            1.mai(2010) to 75641,
            1.mai(2011) to 79216,
            1.mai(2012) to 82122,
            1.mai(2013) to 85245,
            1.mai(2014) to 88370,
            1.mai(2015) to 90068,
            1.mai(2016) to 92576,
            1.mai(2017) to 93634,
            1.mai(2018) to 96883,
            1.mai(2019) to 99858,
            1.mai(2020) to 101351,
            1.mai(2021) to 106399,
        ).let {
            if (ApplicationConfig.isNotProd()) {
                log.warn("Inkluderer fiktiv G-verdi for 2022. Skal ikke dukke opp i prod!")
                // TODO(satsfactory_grunnbeløp_2022) Husk å bytt denne til nyeste før merge til master.
                it + (1.mai(2022) to 107099)
            } else it
        },
    ),
    private val minsteÅrligYtelseForUføretrygdede: MinsteÅrligYtelseForUføretrygdedeFactory = MinsteÅrligYtelseForUføretrygdedeFactory.createFromFaktorer(
        ordinær = listOf(
            1.januar(2015) to Faktor(2.28),
        ),
        høy = listOf(
            1.januar(2015) to Faktor(2.48),
        ),
    ),
    override val formuegrenserFactory: FormuegrenserFactory = FormuegrenserFactory.createFromGrunnbeløp(
        Nel.fromListUnsafe(
            grunnbeløpFactory.månedTilGrunnbeløp.values.toList(),
        ),
    ),
) : SatsFactory {
    override fun fullSupplerendeStønad(satskategori: Satskategori): FullSupplerendeStønadFactory {
        return when (satskategori) {
            Satskategori.ORDINÆR -> FullSupplerendeStønadFactory.Ordinær.Ufør(grunnbeløpFactory, minsteÅrligYtelseForUføretrygdede)
            Satskategori.HØY -> FullSupplerendeStønadFactory.Høy.Ufør(grunnbeløpFactory, minsteÅrligYtelseForUføretrygdede)
        }
    }

    override fun høy(måned: Måned): FullSupplerendeStønadForMåned {
        return fullSupplerendeStønad(Satskategori.HØY).forMåned(måned)
    }

    override fun ordinær(måned: Måned): FullSupplerendeStønadForMåned {
        return fullSupplerendeStønad(Satskategori.ORDINÆR).forMåned(måned)
    }
}
