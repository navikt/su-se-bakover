package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import kotlin.math.roundToInt

internal data class BeregningMedFradragFordeltOverHelePerioden(
    private val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>
) : AbstractBeregning() {
    private val beregning = beregn()

    override fun getSumYtelse() = beregning.values
        .sumBy { it.getSumYtelse() }

    override fun getSumFradrag() = beregning.values
        .sumByDouble { it.getSumFradrag() }

    override fun getSumYtelse(periode: Periode) = periode.tilMånedsperioder()
        .sumBy { beregning[it]?.getSumYtelse() ?: 0 }

    override fun getFradrag(periode: Periode) = periode.tilMånedsperioder()
        .sumByDouble { beregning[it]?.getSumFradrag() ?: 0.0 }.roundToInt()

    override fun getSumYtelseErUnderMinstebeløp() = getSumYtelse() < Sats.toProsentAvHøy(periode)

    private fun beregn(): Map<Periode, Månedsberegning> {
        val perioder = periode.tilMånedsperioder()

        val periodiserteFradrag = fradrag.flatMap {
            FradragFactory.ny(
                type = it.getFradragstype(),
                beløp = it.getTotaltFradrag(),
                periode = periode,
                utenlandskInntekt = it.getUtenlandskInntekt()
            ).periodiser()
        }
            .groupBy { it.periode() }

        return perioder.map {
            it to MånedsberegningFactory.ny(
                periode = it,
                sats = sats,
                fradrag = periodiserteFradrag[it] ?: emptyList()
            )
        }.toMap()
    }

    override fun getSats(): Sats = sats
    override fun getMånedsberegninger(): List<Månedsberegning> = beregning.values.toList()
    override fun getFradrag(): List<Fradrag> = fradrag

    override fun periode(): Periode = periode
}
