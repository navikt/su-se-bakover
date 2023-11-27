package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.tid.periode.Måned
import sats.domain.FullSupplerendeStønadFactory
import sats.domain.FullSupplerendeStønadForMåned
import sats.domain.Knekkpunkt
import sats.domain.Satskategori
import sats.domain.grunnbeløp.GrunnbeløpFactory
import sats.domain.grunnbeløp.GrunnbeløpForMåned
import vilkår.formue.domain.FormuegrenserFactory

/**
 * Satser for supplerende stønad som er gyldig på en gitt dato.
 * Denne er knyttet til ikrafttredelse-datoen i tilhørende lovendringer.
 * Vi ønsker å skille denne fra virkningstidspunktet, da disse har begynt å skille lag i senere år.
 */
class SatsFactoryForSupplerendeStønadPåKnekkpunkt(
    private val grunnbeløpFactory: GrunnbeløpFactory,
    override val formuegrenserFactory: FormuegrenserFactory,
    private val uføreOrdinær: FullSupplerendeStønadFactory.Ordinær.Ufør,
    private val uføreHøy: FullSupplerendeStønadFactory.Høy.Ufør,
    private val alderOrdinær: FullSupplerendeStønadFactory.Ordinær.Alder,
    private val alderHøy: FullSupplerendeStønadFactory.Høy.Alder,
    override val knekkpunkt: Knekkpunkt,
    override val tidligsteTilgjengeligeMåned: Måned,
) : SatsFactory {

    /** full supplerende stønad for uføre*/
    override fun forSatskategoriUføre(måned: Måned, satskategori: Satskategori): FullSupplerendeStønadForMåned.Uføre {
        return when (satskategori) {
            Satskategori.ORDINÆR -> uføreOrdinær.forMåned(måned)
            Satskategori.HØY -> uføreHøy.forMåned(måned)
        }
    }

    override fun forSatskategoriAlder(måned: Måned, satskategori: Satskategori): FullSupplerendeStønadForMåned.Alder {
        return when (satskategori) {
            Satskategori.ORDINÆR -> alderOrdinær.forMåned(måned)
            Satskategori.HØY -> alderHøy.forMåned(måned)
        }
    }

    override fun høyUføre(måned: Måned): FullSupplerendeStønadForMåned.Uføre {
        return uføreHøy.forMåned(måned)
    }

    override fun høyAlder(måned: Måned): FullSupplerendeStønadForMåned.Alder {
        return alderHøy.forMåned(måned)
    }

    override fun ordinærUføre(måned: Måned): FullSupplerendeStønadForMåned.Uføre {
        return uføreOrdinær.forMåned(måned)
    }

    override fun ordinærAlder(måned: Måned): FullSupplerendeStønadForMåned.Alder {
        return alderOrdinær.forMåned(måned)
    }

    override fun grunnbeløp(måned: Måned): GrunnbeløpForMåned {
        return grunnbeløpFactory.forMåned(måned)
    }
}
