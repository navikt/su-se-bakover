package no.nav.su.se.bakover.domain.beregning.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.IFradrag
import java.util.UUID
import kotlin.math.roundToInt

interface IBeregning : PeriodisertInformasjon {
    fun id(): UUID
    fun opprettet(): Tidspunkt
    fun sats(): Sats
    fun månedsberegninger(): List<IMånedsberegning>
    fun fradrag(): List<IFradrag>
    fun totalSum(): Int
    fun totaltFradrag(): Int
    fun sum(periode: Periode): Int
    fun fradrag(periode: Periode): Int
    fun sumUnderMinstegrense(): Boolean
}

abstract class AbstractBeregning : IBeregning {
    private val id by lazy { UUID.randomUUID() }
    private val opprettet by lazy { Tidspunkt.now() }
    override fun id(): UUID = id
    override fun opprettet() = opprettet
}

internal data class Beregning(
    private val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<IFradrag>
) : AbstractBeregning() {
    private val beregning = beregn()

    override fun totalSum() = beregning.values
        .sumByDouble { it.sum() }.roundToInt()

    override fun totaltFradrag() = beregning.values
        .sumByDouble { it.fradrag() }.roundToInt()

    override fun sum(periode: Periode) = periode.tilMånedsperioder()
        .sumByDouble { beregning[it]?.sum() ?: 0.0 }.roundToInt()

    override fun fradrag(periode: Periode) = periode.tilMånedsperioder()
        .sumByDouble { beregning[it]?.fradrag() ?: 0.0 }.roundToInt()

    override fun sumUnderMinstegrense() = totalSum() < Sats.toProsentAvHøy(periode)

    private fun beregn(): Map<Periode, IMånedsberegning> {
        val perioder = periode.tilMånedsperioder()
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

    override fun sats(): Sats = sats
    override fun månedsberegninger(): List<IMånedsberegning> = beregning.values.toList()
    override fun fradrag(): List<IFradrag> = fradrag

    override fun periode(): Periode = periode
}

data class BeregningDbWrapper(
    private val id: UUID,
    private val tidspunkt: Tidspunkt,
    private val beregning: IBeregning
) : AbstractBeregning(), IBeregning by beregning {
    override fun id(): UUID = id
    override fun opprettet() = tidspunkt
}
