package no.nav.su.se.bakover.domain.brev.beregning

data class Beregning(
    val ytelsePerMåned: Int,
    val satsbeløpPerMåned: Int,
    val epsFribeløp: Double,
    val fradrag: Fradrag?,
)
