package no.nav.su.se.bakover.domain.brev.beregning

data class Fradrag(
    val bruker: List<Månedsfradrag>,
    val eps: Eps,
) {
    data class Eps(
        val fradrag: List<Månedsfradrag>,
        val harFradragMedSumSomErLavereEnnFribeløp: Boolean,
    )
}
