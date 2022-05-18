package no.nav.su.se.bakover.domain.vilkår

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.roundToDecimals
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import no.nav.su.se.bakover.domain.satser.Faktor
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
    val ikrafttredelse: LocalDate = grunnbeløpForMåned.ikrafttredelse

    val formuegrense: BigDecimal = grunnbeløpForMåned.grunnbeløpPerÅr.toBigDecimal().multiply(faktor.toBigDecimal())
    val formuegrenseMedToDesimaler: Double = grunnbeløpForMåned.grunnbeløpPerÅr.toBigDecimal().multiply(faktor.toBigDecimal()).roundToDecimals(2)

    val måned: Måned = grunnbeløpForMåned.måned

    init {
        require(grunnbeløpForMåned.måned == måned)
        require(måned.fraOgMed >= ikrafttredelse)
    }
}

fun NonEmptyList<FormuegrenseForMåned>.periode(): Periode = this.map { it.måned }.also {
    it.erSammenhengendeSortertOgUtenDuplikater()
}.minAndMaxOf()
