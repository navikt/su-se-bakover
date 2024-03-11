package beregning.domain

import beregning.domain.fradrag.likeFradrag

infix fun Månedsberegning.likehetUtenDato(other: Månedsberegning): Boolean {
    return this.getSumYtelse() == other.getSumYtelse() &&
        this.getSumFradrag() == other.getSumFradrag() &&
        this.getBenyttetGrunnbeløp() == other.getBenyttetGrunnbeløp() &&
        this.getSats() == other.getSats() &&
        this.getSatsbeløp() == other.getSatsbeløp() &&
        this.getFradrag().likeFradrag(other.getFradrag())
}

fun List<Månedsberegning>.sorterMånedsberegninger(): List<Månedsberegning> {
    return this
        .sortedBy { it.periode.fraOgMed }
}
