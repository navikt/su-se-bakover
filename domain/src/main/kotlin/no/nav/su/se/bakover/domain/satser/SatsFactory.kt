package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpFactory
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory

/**
 * Inneholder alle satsene som brukes av Supplerende Stønad alder og uføre; i.e. full supplerende stønad, grunnbeløp, garantipensjon og minste årlig ytelse for uføretrygdede.
 * Full Supplerende Stønad er en kombinasjon av grunnbeløp og en faktor, hvor faktoren er for
 * - ufør) minste årlig ytelse for uføretrygdede
 * - alder) garantipensjon
 */
interface SatsFactory {
    fun fullSupplerendeStønadHøy(): FullSupplerendeStønadFactory.Høy
    fun fullSupplerendeStønadOrdinær(): FullSupplerendeStønadFactory.Ordinær
    fun fullSupplerendeStønad(satskategori: Satskategori): FullSupplerendeStønadFactory = when (satskategori) {
        Satskategori.ORDINÆR -> fullSupplerendeStønadOrdinær()
        Satskategori.HØY -> fullSupplerendeStønadHøy()
    }
    val grunnbeløpFactory: GrunnbeløpFactory
    val formuegrenserFactory: FormuegrenserFactory
}
