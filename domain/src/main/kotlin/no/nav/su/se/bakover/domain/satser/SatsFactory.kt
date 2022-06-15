package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import java.time.LocalDate
import java.time.YearMonth

/**
 * Inneholder alle satsene som brukes av Supplerende Stønad alder og uføre; i.e. full supplerende stønad, grunnbeløp, garantipensjon og minste årlig ytelse for uføretrygdede.
 * Full Supplerende Stønad er en kombinasjon av grunnbeløp og en faktor, hvor faktoren er for
 * - ufør) minste årlig ytelse for uføretrygdede
 * - alder) garantipensjon
 */
interface SatsFactory {
    fun forSatskategoriUføre(
        måned: Måned,
        satskategori: Satskategori,
    ): FullSupplerendeStønadForMåned.Uføre

    /**  Hvordan verden så ut på denne datoen. */
    val knekkpunkt: Knekkpunkt

    /**
     * Inclusive. Vi har ikke satser før denne måneden, dette året.
     */
    val tidligsteTilgjengeligeMåned: Måned

    /**
     * TODO jah: Bytt ut denne med en formuegrenseForMåned og formuegrenser etter en gitt dato/måned.
     */
    val formuegrenserFactory: FormuegrenserFactory

    /** høy supplerende stønad for uføre */
    fun høyUføre(måned: Måned): FullSupplerendeStønadForMåned.Uføre

    /** høy supplerende stønad for alder */
    fun høyAlder(måned: Måned): FullSupplerendeStønadForMåned.Alder

    /** ordinær supplerende stønad for uføre */
    fun ordinærUføre(måned: Måned): FullSupplerendeStønadForMåned.Uføre

    /** ordinær supplerende stønad for alder */
    fun ordinærAlder(måned: Måned): FullSupplerendeStønadForMåned.Alder

    fun grunnbeløp(dato: LocalDate): GrunnbeløpForMåned {
        return grunnbeløp(Måned.fra(YearMonth.of(dato.year, dato.month)))
    }

    fun grunnbeløp(måned: Måned): GrunnbeløpForMåned
}
