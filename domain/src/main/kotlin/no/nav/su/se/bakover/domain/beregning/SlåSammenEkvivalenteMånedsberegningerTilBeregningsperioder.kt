package no.nav.su.se.bakover.domain.beregning

import beregning.domain.EkvivalenteMånedsberegninger
import beregning.domain.Månedsberegning
import beregning.domain.likehetUtenDato
import beregning.domain.sorterMånedsberegninger

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
data class SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
    private val månedsberegninger: List<Månedsberegning>,
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
}
