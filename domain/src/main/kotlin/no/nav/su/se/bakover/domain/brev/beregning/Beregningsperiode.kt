package no.nav.su.se.bakover.domain.brev.beregning

data class Beregningsperiode(
    val ytelsePerMåned: Int,
    val satsbeløpPerMåned: Int,
    val epsFribeløp: Int,
    val fradrag: Fradrag,
    val periode: BrevPeriode,
    val sats: String,
)

data class BrevPeriode(
    val fraOgMed: String,
    val tilOgMed: String
)
