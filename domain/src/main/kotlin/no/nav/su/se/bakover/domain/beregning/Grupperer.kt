package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag

object Grupperer {
    fun grupper(månedsberegninger: List<Månedsberegning>): Map<Periode, List<Månedsberegning>> {
        val grupper = mutableListOf<MutableList<Månedsberegning>>()
        månedsberegninger.sortedBy { it.getPeriode().getFraOgMed() }.forEach { månedsberegning ->
            when {
                grupper.isEmpty() -> grupper.add(mutableListOf(månedsberegning))
                grupper.last().last() likehetUtenDato månedsberegning -> grupper.last().add(månedsberegning)
                else -> grupper.add(mutableListOf(månedsberegning))
            }
        }

        return grupper.map {
            Periode(
                fraOgMed = it.minByOrNull { it.getPeriode().getFraOgMed() }!!.getPeriode().getFraOgMed(),
                tilOgMed = it.maxByOrNull { it.getPeriode().getTilOgMed() }!!.getPeriode().getTilOgMed()
            ) to it
        }.toMap()
    }

    internal infix fun Månedsberegning.likehetUtenDato(other: Månedsberegning): Boolean =
        this.getSumYtelse() == other.getSumYtelse() &&
            this.getSumFradrag() == other.getSumFradrag() &&
            this.getBenyttetGrunnbeløp() == other.getBenyttetGrunnbeløp() &&
            this.getSats() == other.getSats() &&
            this.getSatsbeløp() == other.getSatsbeløp() &&
            this.getFradrag().cmpFradrag(other.getFradrag())

    internal infix fun Fradrag.likhetUtenDato(other: Fradrag): Boolean =
        this.getFradragstype() == other.getFradragstype() &&
            this.getMånedsbeløp() == other.getMånedsbeløp() &&
            this.getUtenlandskInntekt() == other.getUtenlandskInntekt() &&
            this.getTilhører() == other.getTilhører()

    internal infix fun List<Fradrag>.cmpFradrag(other: List<Fradrag>): Boolean {
        if (this.size != other.size) return false
        val sortedThis = this.sortedBy { it.getFradragstype() }
        val sortedThat = other.sortedBy { it.getFradragstype() }
        return sortedThis.zip(sortedThat) { a, b -> a likhetUtenDato b }.all { it }
    }
}
