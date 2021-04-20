package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.limitedUpwardsTo
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.positiveOrZero
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import kotlin.math.roundToInt

internal data class PeriodisertBeregning(
    private val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>
) : Månedsberegning {
    init {
        require(fradrag.all { it.getPeriode() == periode }) { "Fradrag må være gjeldende for aktuell måned" }
        require(periode.getAntallMåneder() == 1) { "Månedsberegning kan kun utføres for en enkelt måned" }
    }

    override fun getSumYtelse(): Int = (getSatsbeløp() - getSumFradrag())
        .positiveOrZero()
        .roundToInt()

    override fun getSumFradrag() = fradrag
        .sumByDouble { it.getMånedsbeløp() }
        .limitedUpwardsTo(getSatsbeløp())

    override fun getBenyttetGrunnbeløp(): Int = Grunnbeløp.`1G`.fraDato(periode.getFraOgMed()).toInt()
    override fun getSats(): Sats = sats
    override fun getSatsbeløp(): Double = sats.periodiser(periode).getValue(periode)
    override fun getFradrag(): List<Fradrag> = fradrag
    override fun getPeriode(): Periode = periode

    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false
}
