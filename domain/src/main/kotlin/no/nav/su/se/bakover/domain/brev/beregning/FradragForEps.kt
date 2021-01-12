package no.nav.su.se.bakover.domain.brev.beregning

data class FradragForEps(
    val fradrag: List<Månedsfradrag>,
    val sum: Double,
)
