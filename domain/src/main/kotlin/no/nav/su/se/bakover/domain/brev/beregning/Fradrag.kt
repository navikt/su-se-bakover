package no.nav.su.se.bakover.domain.brev.beregning

data class Fradrag(
    val bruker: List<Månedsfradrag>,
    val eps: List<Månedsfradrag>,
)
