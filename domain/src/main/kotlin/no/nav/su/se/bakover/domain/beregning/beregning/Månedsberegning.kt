package no.nav.su.se.bakover.domain.beregning.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.common.positiveOrZero
import no.nav.su.se.bakover.common.sumLimitedUpwardsTo
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag

data class Månedsberegning(
    private val periode: Periode,
    private val satsbeløp: Double,
    private val fradrag: List<Fradrag>
) : PeriodisertInformasjon {
    init {
        require(fradrag.all { it.periode() == periode }) { "Fradrag må være gjeldende for aktuell måned" }
        require(periode.antallMåneder() == 1) { "Månedsberegning kan kun utføres for en enkelt måned" }
    }

    fun sum() = (satsbeløp - fradrag())
        .positiveOrZero()

    fun fradrag() = fradrag
        .sumByDouble { it.månedsbeløp() }
        .sumLimitedUpwardsTo(satsbeløp)

    override fun periode(): Periode = periode
}
