package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag

object Grupperer {
    fun grupper(månedsberegninger: List<Månedsberegning>): Map<Periode, List<Månedsberegning>> {
        val grupper = mutableListOf<MutableList<Månedsberegning>>()
        månedsberegninger.sortedBy { it.getPeriode().getFraOgMed() }.forEach { månedsberegning ->
            when {
                grupper.isEmpty() -> grupper.add(mutableListOf(månedsberegning))
                grupper.last().last()
                    .getSumYtelse() == månedsberegning.getSumYtelse() -> grupper.last().add(
                    månedsberegning
                )
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

    private infix fun Månedsberegning.likehetUtenDato(other: Månedsberegning): Boolean =
        this.getSumYtelse() == other.getSumYtelse() &&
            this.getSumFradrag() == other.getSumFradrag() &&
            this.getBenyttetGrunnbeløp() == other.getBenyttetGrunnbeløp() &&
            this.getSats() == other.getSats() &&
            this.getSatsbeløp() == other.getSatsbeløp() &&
            this.getFradrag() == other.getFradrag() // TODO fix

    private infix fun Fradrag.likhetUtenDato(other: Fradrag): Boolean =
        this.getFradragstype() == other.getFradragstype() &&
            this.getMånedsbeløp() == other.getMånedsbeløp() &&
            this.getUtenlandskInntekt() == other.getUtenlandskInntekt() &&
            this.getTilhører() == other.getTilhører()
}
