package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.periode.Periode

data class Beregningsperiode(
    val ytelsePerMåned: Int,
    val satsbeløpPerMåned: Int,
    val epsFribeløp: Double,
    val fradrag: Fradrag,
    val periode: Periode
)
