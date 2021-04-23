package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag

/**
 * Join equivalent månedsberegninger to form distinct periods of ytelse and fradrag.
 *
 * A few examples of the intent.
 * Input:
 *     a   a   a   b   a   a
 *   |---|---|---|---|---|---|
 * Yields:
 *         a	   b     a
 *   |-----------|–––|-------|
 *
 * Input:
 *     a   a   a   a   a   a
 *   |---|---|---|---|---|---|
 * Yields:
 *               a
 *   |-----------------------|
 * Input:
 *     a   b   b   a   b   c
 *   |---|---|---|---|---|---|
 * Yields:
 *     a     b     a   b   c
 *   |---|-------|---|---|---|
 */
internal data class SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
    private val månedsberegninger: List<Månedsberegning>
) {
    val beregningsperioder: List<EkvivalenteMånedsberegninger> = mutableListOf<MutableList<Månedsberegning>>().apply {
        månedsberegninger.sorterMånedsberegninger().forEach { månedsberegning ->
            when {
                this.isEmpty() -> this.add(mutableListOf(månedsberegning))
                this.last().sisteMånedsberegningErLikOgTilstøtende(månedsberegning) -> this.last().add(månedsberegning)
                else -> this.add(mutableListOf(månedsberegning))
            }
        }
    }.map {
        EkvivalenteMånedsberegninger(it)
    }

    private fun List<Månedsberegning>.sisteMånedsberegningErLikOgTilstøtende(månedsberegning: Månedsberegning): Boolean =
        this.last().let { sisteMånedsberegning ->
            sisteMånedsberegning likehetUtenDato månedsberegning && sisteMånedsberegning.periode tilstøter månedsberegning.periode
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

    private fun List<Månedsberegning>.sorterMånedsberegninger() = this
        .sortedBy { it.periode.fraOgMed }

    private fun List<Fradrag>.sorterFradrag() = this
        .sortedBy { it.getMånedsbeløp() }
        .sortedBy { it.getFradragstype() }
        .sortedBy { it.getTilhører() }
}

/**
 * Represents a group of adjacent [Månedsberegning], sharing all properties but [Periode] (also for fradrag).
 * Implements the interface to act as a regular månedsberegning, but overrides the to return a period
 * representing the minimum and maximum dates for the group of månedsberegninger contained.
 */
internal data class EkvivalenteMånedsberegninger(
    private val månedsberegninger: List<Månedsberegning>
) : Månedsberegning by månedsberegninger.first() {
    override val periode: Periode = Periode.create(
        fraOgMed = månedsberegninger.minOf { it.periode.fraOgMed },
        tilOgMed = månedsberegninger.maxOf { it.periode.tilOgMed },
    )

    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false
}
