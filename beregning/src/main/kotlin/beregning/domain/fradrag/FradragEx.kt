package beregning.domain.fradrag

import vilkår.inntekt.domain.grunnlag.Fradrag

infix fun Fradrag.likhetUtenDato(other: Fradrag): Boolean =
    this.fradragstype == other.fradragstype &&
        this.månedsbeløp == other.månedsbeløp &&
        this.utenlandskInntekt == other.utenlandskInntekt &&
        this.tilhører == other.tilhører

infix fun List<Fradrag>.likeFradrag(other: List<Fradrag>): Boolean {
    if (this.size != other.size) return false
    val sortedThis = this.sorterFradrag()
    val sortedThat = other.sorterFradrag()
    return sortedThis.zip(sortedThat) { a, b -> a likhetUtenDato b }.all { it }
}

fun List<Fradrag>.sorterFradrag() = this
    .sortedBy { it.månedsbeløp }
    .sortedBy { it.fradragstype.kategori }
    .sortedBy { it.tilhører }
