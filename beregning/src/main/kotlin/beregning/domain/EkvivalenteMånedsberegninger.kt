package beregning.domain

import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import satser.domain.supplerendestønad.FullSupplerendeStønadForMåned
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragForMåned

/**
 * Represents a group of adjacent [Månedsberegning], sharing all properties but [Periode] (also for fradrag).
 * Implements the interface to act as a regular månedsberegning, but overrides the to return a period
 * representing the minimum and maximum dates for the group of månedsberegninger contained.
 */
data class EkvivalenteMånedsberegninger(
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
