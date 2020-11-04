package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.limitedUpwardsTo
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.positiveOrZero
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag

internal data class PeriodeMånedsberegning(
    private val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>
) : Månedsberegning {
    init {
        require(fradrag.all { it.periode() == periode }) { "Fradrag må være gjeldende for aktuell måned" }
        require(periode.antallMåneder() == 1) { "Månedsberegning kan kun utføres for en enkelt måned" }
    }

    override fun getSumYtelse() = (getSatsbeløp() - getSumFradrag())
        .positiveOrZero()

    override fun getSumFradrag() = fradrag
        .sumByDouble { it.månedsbeløp() }
        .limitedUpwardsTo(getSatsbeløp())

    override fun getBenyttetGrunnbeløp(): Int = Grunnbeløp.`1G`.fraDato(periode.fraOgMed()).toInt()
    override fun getSats(): Sats = sats
    override fun getSatsbeløp(): Double = sats.periodiser(periode).getValue(periode)

    override fun periode(): Periode = periode
}
