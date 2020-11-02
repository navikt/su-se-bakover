package no.nav.su.se.bakover.domain.beregning.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.AbstractFradrag
import java.util.UUID
import kotlin.math.roundToInt

interface IBeregning : PeriodisertInformasjon {
    fun id(): UUID
    fun opprettet(): Tidspunkt
    fun sats(): Sats
    fun månedsberegninger(): List<AbstractMånedsberegning>
    fun fradrag(): List<AbstractFradrag>
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
    private val fradrag: List<AbstractFradrag>
) : AbstractBeregning() {
    private val beregning = beregn()

    override fun totalSum() = beregning.values
        .sumByDouble { it.sum() }.roundToInt()

    override fun totaltFradrag() = beregning.values
        .sumByDouble { it.fradrag() }.roundToInt()

    override fun sum(periode: Periode) = periode.periodiserMåneder()
        .sumByDouble { beregning[it]?.sum() ?: 0.0 }.roundToInt()

    override fun fradrag(periode: Periode) = periode.periodiserMåneder()
        .sumByDouble { beregning[it]?.fradrag() ?: 0.0 }.roundToInt()

    override fun sumUnderMinstegrense() = totalSum() < kalkuler2ProsentAvHøySats()

    private fun beregn(): Map<Periode, AbstractMånedsberegning> {
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

    private fun kalkuler2ProsentAvHøySats() = sats.toProsentAvHøySats(periode)

    override fun sats(): Sats = sats
    override fun månedsberegninger(): List<AbstractMånedsberegning> = beregning.values.toList()
    override fun fradrag(): List<AbstractFradrag> = fradrag

    override fun periode(): Periode = periode
}

data class BeregningDbWrapper(
    private val id: UUID,
    private val tidspunkt: Tidspunkt,
    private val beregning: AbstractBeregning
) : AbstractBeregning(), IBeregning by beregning {
    override fun id(): UUID = id
    override fun opprettet() = tidspunkt
}
