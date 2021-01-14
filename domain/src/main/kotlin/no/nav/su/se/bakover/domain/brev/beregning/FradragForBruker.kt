package no.nav.su.se.bakover.domain.brev.beregning

data class FradragForBruker(
    val fradrag: List<Månedsfradrag>,
    val sum: Double,
)
