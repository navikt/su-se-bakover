package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag

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

internal sealed class SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder {
    data class Utbetaling(
        private val månedsberegninger: List<Månedsberegning>,
        private val uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    ) : SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder() {
        val beregningsperioder: List<EkvivalenteMånedsberegningerOgUføre> =
            getMånedsberegningerOgTilhørendeUføregrunnlag().slåSammenMånedsberegningerOgUføregrunnlagPåMånedsberegninger()

        private fun getMånedsberegningerOgTilhørendeUføregrunnlag(): List<MånedsberegningOgTilhørendeUføregrunnlag> {
            val månedsberegningerOgTilhørendeUføregrunnlag = mutableListOf<MånedsberegningOgTilhørendeUføregrunnlag>()

            månedsberegninger.sorterMånedsberegninger().forEach { månedsberegning ->
                månedsberegningerOgTilhørendeUføregrunnlag.add(
                    finnSisteTilhørendeUføregrunnlag(månedsberegning),
                )
            }

            return månedsberegningerOgTilhørendeUføregrunnlag
        }

        private fun finnSisteTilhørendeUføregrunnlag(
            månedsberegning: Månedsberegning,
        ): MånedsberegningOgTilhørendeUføregrunnlag {
            var sistTilhørendeUføregrunnlag: Grunnlag.Uføregrunnlag? = null

            uføregrunnlag.forEach {
                if (it.periode.inneholder(månedsberegning.periode) && sistTilhørendeUføregrunnlag == null) {
                    sistTilhørendeUføregrunnlag = it
                }

                if (it.periode.inneholder(månedsberegning.periode) && it.opprettet.instant.isAfter(
                        sistTilhørendeUføregrunnlag?.opprettet?.instant,
                    )
                ) {
                    sistTilhørendeUføregrunnlag = it
                }
            }
            return MånedsberegningOgTilhørendeUføregrunnlag(
                månedsberegning = månedsberegning,
                uføregrunnlag = sistTilhørendeUføregrunnlag
                    ?: throw IllegalStateException("Finnes en månedsberegning uten tilhørende uføregrunnlag for perioden"),
            )
        }

        private fun List<MånedsberegningOgTilhørendeUføregrunnlag>.slåSammenMånedsberegningerOgUføregrunnlagPåMånedsberegninger() =
            mutableListOf<MutableList<MånedsberegningOgTilhørendeUføregrunnlag>>().apply {
                this@slåSammenMånedsberegningerOgUføregrunnlagPåMånedsberegninger.sortedBy { it.månedsberegning.periode.fraOgMed }
                    .forEach { månedsberegningOgTilhørendeUføregrunnlag ->
                        when {
                            this.isEmpty() -> this.add(mutableListOf(månedsberegningOgTilhørendeUføregrunnlag))
                            this.last()
                                .sisteMånedsberegningOgUføreErLikOgTilstøtende(månedsberegningOgTilhørendeUføregrunnlag) -> this.last()
                                .add(månedsberegningOgTilhørendeUføregrunnlag)
                            else -> this.add(mutableListOf(månedsberegningOgTilhørendeUføregrunnlag))
                        }
                    }
            }.map {
                EkvivalenteMånedsberegningerOgUføre(it)
            }

        private fun List<MånedsberegningOgTilhørendeUføregrunnlag>.sisteMånedsberegningOgUføreErLikOgTilstøtende(
            månedsberegningOgTilhørendeUføregrunnlag: MånedsberegningOgTilhørendeUføregrunnlag,
        ): Boolean =
            this.last().let { sistMånedsberegningOgTilhørendeUføregrunnlag ->
                sistMånedsberegningOgTilhørendeUføregrunnlag.månedsberegning likehetUtenDato månedsberegningOgTilhørendeUføregrunnlag.månedsberegning &&
                    sistMånedsberegningOgTilhørendeUføregrunnlag.månedsberegning.periode tilstøter månedsberegningOgTilhørendeUføregrunnlag.månedsberegning.periode &&
                    sistMånedsberegningOgTilhørendeUføregrunnlag.uføregrunnlag.uføregrad.value == månedsberegningOgTilhørendeUføregrunnlag.uføregrunnlag.uføregrad.value
            }
    }

    data class Brev(
        private val månedsberegninger: List<Månedsberegning>,
    ) : SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder() {
        val beregningsperioder: List<EkvivalenteMånedsberegninger> =
            mutableListOf<MutableList<Månedsberegning>>().apply {
                månedsberegninger.sorterMånedsberegninger().forEach { månedsberegning ->
                    when {
                        this.isEmpty() -> this.add(mutableListOf(månedsberegning))
                        this.last().sisteMånedsberegningErLikOgTilstøtende(månedsberegning) -> this.last()
                            .add(månedsberegning)
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
    }
}

internal data class MånedsberegningOgTilhørendeUføregrunnlag(
    val månedsberegning: Månedsberegning,
    val uføregrunnlag: Grunnlag.Uføregrunnlag,
)

private infix fun Månedsberegning.likehetUtenDato(other: Månedsberegning): Boolean =
    this.getSumYtelse() == other.getSumYtelse() &&
        this.getSumFradrag() == other.getSumFradrag() &&
        this.getBenyttetGrunnbeløp() == other.getBenyttetGrunnbeløp() &&
        this.getSats() == other.getSats() &&
        this.getSatsbeløp() == other.getSatsbeløp() &&
        this.getFradrag().likeFradrag(other.getFradrag())

private infix fun Fradrag.likhetUtenDato(other: Fradrag): Boolean =
    this.fradragstype == other.fradragstype &&
        this.månedsbeløp == other.månedsbeløp &&
        this.utenlandskInntekt == other.utenlandskInntekt &&
        this.tilhører == other.tilhører

private infix fun List<Fradrag>.likeFradrag(other: List<Fradrag>): Boolean {
    if (this.size != other.size) return false
    val sortedThis = this.sorterFradrag()
    val sortedThat = other.sorterFradrag()
    return sortedThis.zip(sortedThat) { a, b -> a likhetUtenDato b }.all { it }
}

private fun List<Månedsberegning>.sorterMånedsberegninger() = this
    .sortedBy { it.periode.fraOgMed }

private fun List<Fradrag>.sorterFradrag() = this
    .sortedBy { it.månedsbeløp }
    .sortedBy { it.fradragstype }
    .sortedBy { it.tilhører }

/**
 * Represents a group of adjacent [Månedsberegning], sharing all properties but [Periode] (also for fradrag).
 * Implements the interface to act as a regular månedsberegning, but overrides the to return a period
 * representing the minimum and maximum dates for the group of månedsberegninger contained.
 */
internal data class EkvivalenteMånedsberegningerOgUføre(
    val månedsberegningerOgUføregrunnlag: List<MånedsberegningOgTilhørendeUføregrunnlag>,
) : Månedsberegning by månedsberegningerOgUføregrunnlag.first().månedsberegning {
    override val periode: Periode = Periode.create(
        fraOgMed = månedsberegningerOgUføregrunnlag.minOf { it.månedsberegning.periode.fraOgMed },
        tilOgMed = månedsberegningerOgUføregrunnlag.maxOf { it.månedsberegning.periode.tilOgMed },
    )
    val uføregrunnlag = månedsberegningerOgUføregrunnlag.first().uføregrunnlag

    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false
}

internal data class EkvivalenteMånedsberegninger(
    private val månedsberegninger: List<Månedsberegning>,
) : Månedsberegning by månedsberegninger.first() {
    override val periode: Periode = Periode.create(
        fraOgMed = månedsberegninger.minOf { it.periode.fraOgMed },
        tilOgMed = månedsberegninger.maxOf { it.periode.tilOgMed },
    )

    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false
}
