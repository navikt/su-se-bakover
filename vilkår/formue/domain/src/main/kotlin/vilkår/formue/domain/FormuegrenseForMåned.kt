package vilkår.formue.domain

import arrow.core.NonEmptyList
import grunnbeløp.domain.GrunnbeløpForMåned
import no.nav.su.se.bakover.common.domain.Faktor
import no.nav.su.se.bakover.common.domain.extensions.roundToDecimals
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Formuegrensen ([paragraf 8](https://lovdata.no/lov/2005-04-29-21/§8)) for Supplerende Stønad er per 2016-01-01 definert som:
 * > Dersom ein søkjar eller ektemaken har formue over 0,5 ganger grunnbeløpet, skal ein gi avslag på søknaden.
 * > Som formue vert ikkje rekna vanleg bustad eller vanlege ting til dagleg bruk.
 * > Departementet kan gi forskrift til utfylling av reglane i paragrafen her.
 */
data class FormuegrenseForMåned(
    val grunnbeløpForMåned: GrunnbeløpForMåned,
    val faktor: Faktor = Faktor(0.5),
) {
    /**
     * Dette er ikke nødvendigvis det samme som ikrafttredelse. Grunnbeløpet kan f.eks. tre i kraft rundt 20. mai., mens virkningstidspunktet de siste årene har vært 1. mai.
     * Når det kommer til lover kan ikrafttredesen være både før og etter virkningstidspunktet.
     */
    val virkningstidspunkt: LocalDate = grunnbeløpForMåned.virkningstidspunkt
    val ikrafttredelse: LocalDate = grunnbeløpForMåned.ikrafttredelse

    val formuegrense: BigDecimal = grunnbeløpForMåned.grunnbeløpPerÅr.toBigDecimal().multiply(faktor.toBigDecimal())
    val formuegrenseMedToDesimaler: Double = grunnbeløpForMåned.grunnbeløpPerÅr.toBigDecimal().multiply(faktor.toBigDecimal()).roundToDecimals(2)

    val måned: Måned = grunnbeløpForMåned.måned

    init {
        require(grunnbeløpForMåned.måned == måned)
        require(måned.fraOgMed >= virkningstidspunkt)
    }
}

fun NonEmptyList<FormuegrenseForMåned>.periode(): Periode = this.map { it.måned }.also {
    it.erSammenhengendeSortertOgUtenDuplikater()
}.minAndMaxOf()
