package no.nav.su.se.bakover.domain.brev.beregning

data class FradragForBrev(
    val bruker: List<MånedsfradragForBrev>,
    val eps: Eps,
) {
    data class Eps(
        val fradrag: List<MånedsfradragForBrev>,
        val harFradragMedSumSomErLavereEnnFribeløp: Boolean,
    )
}
