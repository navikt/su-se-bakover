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
    fun forSatskategori(
        måned: Måned,
        satskategori: Satskategori,
    ): FullSupplerendeStønadForMåned

    /**
     * Returnerer en ny factory som opererer med verdiene som var gjeldende [påDato]
     */
    fun gjeldende(påDato: LocalDate): SatsFactory

    val formuegrenserFactory: FormuegrenserFactory

    fun høy(måned: Måned): FullSupplerendeStønadForMåned
    fun ordinær(måned: Måned): FullSupplerendeStønadForMåned
    fun grunnbeløp(dato: LocalDate): GrunnbeløpForMåned {
        return grunnbeløp(Måned(YearMonth.of(dato.year, dato.month)))
    }

    fun grunnbeløp(måned: Måned): GrunnbeløpForMåned
}
