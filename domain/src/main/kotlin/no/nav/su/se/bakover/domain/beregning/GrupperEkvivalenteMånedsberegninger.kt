package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag

data class GrupperEkvivalenteMånedsberegninger(
    private val månedsberegninger: List<Månedsberegning>
) {
    val grupper = mutableListOf<MutableList<Månedsberegning>>().apply {
        månedsberegninger.sorterMånedsberegninger().forEach { månedsberegning ->
            when {
                this.isEmpty() -> this.add(mutableListOf(månedsberegning))
                this.last().sisteMånedsberegningErLikOgTilstøtende(månedsberegning) -> this.last().add(månedsberegning)
                else -> this.add(mutableListOf(månedsberegning))
            }
        }
    }.map {
        GrupperteMånedsberegninger(it)
    }

    private fun List<Månedsberegning>.sisteMånedsberegningErLikOgTilstøtende(månedsberegning: Månedsberegning): Boolean =
        this.last().let { sisteMånedsberegning ->
            sisteMånedsberegning likehetUtenDato månedsberegning && sisteMånedsberegning.getPeriode() tilstøter månedsberegning.getPeriode()
        }

    private infix fun Månedsberegning.likehetUtenDato(other: Månedsberegning): Boolean =
        this.getSumYtelse() == other.getSumYtelse() &&
            this.getSumFradrag() == other.getSumFradrag() &&
            this.getBenyttetGrunnbeløp() == other.getBenyttetGrunnbeløp() &&
            this.getSats() == other.getSats() &&
            this.getSatsbeløp() == other.getSatsbeløp() &&
            this.getFradrag().likeFradrag(other.getFradrag())

    private infix fun Fradrag.likhetUtenDato(other: Fradrag): Boolean =
        this.getFradragstype() == other.getFradragstype() &&
            this.getMånedsbeløp() == other.getMånedsbeløp() &&
            this.getUtenlandskInntekt() == other.getUtenlandskInntekt() &&
            this.getTilhører() == other.getTilhører()

    private infix fun List<Fradrag>.likeFradrag(other: List<Fradrag>): Boolean {
        if (this.size != other.size) return false
        val sortedThis = this.sorterFradrag()
        val sortedThat = other.sorterFradrag()
        return sortedThis.zip(sortedThat) { a, b -> a likhetUtenDato b }.all { it }
    }

    data class GrupperteMånedsberegninger(
        val månedsberegninger: List<Månedsberegning>
    ) : Månedsberegning by månedsberegninger.first() {
        override fun getPeriode(): Periode = Periode(
            fraOgMed = månedsberegninger.minOf { it.getPeriode().getFraOgMed() },
            tilOgMed = månedsberegninger.maxOf { it.getPeriode().getTilOgMed() }
        )
    }

    private fun List<Månedsberegning>.sorterMånedsberegninger() = this
        .sortedBy { it.getPeriode().getFraOgMed() }

    private fun List<Fradrag>.sorterFradrag() = this
        .sortedBy { it.getMånedsbeløp() }
        .sortedBy { it.getFradragstype() }
        .sortedBy { it.getTilhører() }
}
