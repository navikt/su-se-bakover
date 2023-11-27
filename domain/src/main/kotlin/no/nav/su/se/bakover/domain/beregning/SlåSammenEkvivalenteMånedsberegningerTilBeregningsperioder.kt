package no.nav.su.se.bakover.domain.beregning

import behandling.domain.beregning.fradrag.Fradrag
import behandling.domain.beregning.fradrag.FradragFactory
import behandling.domain.beregning.fradrag.FradragForMåned
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import sats.domain.FullSupplerendeStønadForMåned

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

    /**
     * Represents a group of adjacent [Månedsberegning], sharing all properties but [Periode] (also for fradrag).
     * Implements the interface to act as a regular månedsberegning, but overrides the to return a period
     * representing the minimum and maximum dates for the group of månedsberegninger contained.
     */
    internal data class EkvivalenteMånedsberegninger(
        private val månedsberegninger: List<Månedsberegning>,
        private val first: Månedsberegning = månedsberegninger.first(),
    ) : Månedsberegning by first {
        init {
            månedsberegninger.windowed(size = 2, step = 1, partialWindows = false)
                .forEach {
                    if (it.count() == 1) {
                        // alltid ok dersom det bare er et element i listen
                    } else {
                        require(it.count() == 2)
                        require(!it.first().periode.overlapper(it.last().periode)) { "Overlappende månedsberegninger" }
                        require(it.first().periode.tilstøter(it.last().periode)) { "Perioder tilstøter ikke" }
                        require(it.first().likehetUtenDato(it.last())) { "Månedsberegninger ex periode er ulike" }
                    }
                }
        }

        override val periode: Periode = Periode.create(
            fraOgMed = månedsberegninger.minOf { it.periode.fraOgMed },
            tilOgMed = månedsberegninger.maxOf { it.periode.tilOgMed },
        )

        data object UtryggOperasjonException : RuntimeException(
            """
                Utrygg operasjon! Klassen wrapper månedsberegninger som potensielt spenner over flere måneder
                og kan være misvisende dersom denne informasjonen brukes videre.
            """.trimIndent(),
        )

        override val måned: Måned
            get() = throw UtryggOperasjonException

        override val fullSupplerendeStønadForMåned: FullSupplerendeStønadForMåned
            get() = throw UtryggOperasjonException

        override fun getFradrag(): List<FradragForMåned> {
            throw UtryggOperasjonException
        }

        /**
         * Gjenopprett fradragene med [periode]
         */
        fun fradrag(): List<Fradrag> {
            return first.getFradrag().map {
                FradragFactory.nyFradragsperiode(
                    fradragstype = it.fradragstype,
                    månedsbeløp = it.månedsbeløp,
                    periode = periode,
                    utenlandskInntekt = it.utenlandskInntekt,
                    tilhører = it.tilhører,
                )
            }
        }

        override fun equals(other: Any?) = other is EkvivalenteMånedsberegninger &&
            månedsberegninger == other.månedsberegninger
    }
}

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
    .sortedBy { it.fradragstype.kategori }
    .sortedBy { it.tilhører }
