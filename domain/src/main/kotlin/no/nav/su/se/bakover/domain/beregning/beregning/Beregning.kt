package no.nav.su.se.bakover.domain.beregning.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import kotlin.math.roundToInt

data class Beregning(
    private val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>
) : PeriodisertInformasjon {
    private val beregning = beregn()

    fun totalSum() = beregning.values
        .sumByDouble { it.sum() }.roundToInt()

    fun totaltFradrag() = beregning.values
        .sumByDouble { it.fradrag() }.roundToInt()

    fun sum(periode: Periode) = periode.periodiserMåneder()
        .sumByDouble { beregning[it]?.sum() ?: 0.0 }.roundToInt()

    fun fradrag(periode: Periode) = periode.periodiserMåneder()
        .sumByDouble { beregning[it]?.fradrag() ?: 0.0 }.roundToInt()

    fun sumUnderMinstegrense() = totalSum() < kalkuler2ProsentAvHøySats()

    private fun beregn(): Map<Periode, Månedsberegning> {
        val perioder = periode.periodiserMåneder()
        val periodiserteFradrag = fradrag.flatMap { it.periodiser() }
            .groupBy { it.periode() }

        return perioder.map {
            it to Månedsberegning(
                periode = it,
                sats = sats,
                fradrag = periodiserteFradrag[it] ?: emptyList()
            )
        }.toMap()
    }

    private fun kalkuler2ProsentAvHøySats() = beregning.values
        .sumByDouble { Sats.HØY.månedsbeløp(periode.fraOgMed()) * 0.02 }
        .roundToInt()

    override fun periode(): Periode {
        return periode
    }
}
