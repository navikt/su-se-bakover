package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.util.UUID
import kotlin.math.roundToInt

data class Beregning(
    private val id: UUID,
    private val opprettet: Tidspunkt,
    private val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>
) : PeriodisertInformasjon {
    private val beregningsPerioder = beregn()
    fun id(): UUID = id

    fun opprettet(): Tidspunkt = opprettet

    fun getSumYtelse() = beregningsPerioder.values
        .sumByDouble { it.getSumYtelse() }.roundToInt()

    fun getSumFradrag() = beregningsPerioder.values
        .sumByDouble { it.getSumFradrag() }.roundToInt()

    fun getSumYtelse(periode: Periode) = periode.tilMånedsperioder()
        .sumByDouble { beregningsPerioder[it]?.getSumYtelse() ?: 0.0 }.roundToInt()

    fun getFradrag(periode: Periode) = periode.tilMånedsperioder()
        .sumByDouble { beregningsPerioder[it]?.getSumFradrag() ?: 0.0 }.roundToInt()

    fun getSumYtelseErUnderMinstebeløp() = getSumYtelse() < Sats.toProsentAvHøy(periode)

    private fun beregn(): Map<Periode, Månedsberegning> {
        val perioder = periode.tilMånedsperioder()
        val periodiserteFradrag = fradrag.flatMap { it.periodiser() }
            .groupBy { it.periode() }

        return perioder.map {
            it to MånedsberegningFactory.ny(
                periode = it,
                sats = sats,
                fradrag = periodiserteFradrag[it] ?: emptyList()
            )
        }.toMap()
    }

    fun getSats(): Sats = sats
    fun getMånedsberegninger(): List<Månedsberegning> = beregningsPerioder.values.toList()
    fun getFradrag(): List<Fradrag> = fradrag

    override fun periode(): Periode = periode
}
