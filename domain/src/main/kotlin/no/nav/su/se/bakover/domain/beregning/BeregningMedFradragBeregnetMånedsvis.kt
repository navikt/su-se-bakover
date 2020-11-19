package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import java.util.UUID

internal data class BeregningMedFradragBeregnetMånedsvis(
    private val id: UUID = UUID.randomUUID(),
    private val opprettet: Tidspunkt = Tidspunkt.now(),
    private val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>,
    private val fradragStrategy: FradragStrategy
) : Beregning {
    private val beregning = beregn()

    override fun getId(): UUID = id

    override fun getOpprettet(): Tidspunkt = opprettet

    override fun getSumYtelse() = beregning.values
        .sumBy { it.getSumYtelse() }

    override fun getSumFradrag() = beregning.values
        .sumByDouble { it.getSumFradrag() }

    override fun getSumYtelseErUnderMinstebeløp() = getSumYtelse() < Sats.toProsentAvHøy(periode)
    override fun getFradragStrategyName(): FradragStrategyName = fradragStrategy.getName()

    private fun beregn(): Map<Periode, Månedsberegning> {
        val perioder = periode.tilMånedsperioder()

        val beregnetPeriodisertFradrag = fradragStrategy.beregn(fradrag, periode)

        return perioder.map {
            it to MånedsberegningFactory.ny(
                periode = it,
                sats = sats,
                fradrag = beregnetPeriodisertFradrag[it] ?: emptyList()
            )
        }.toMap()
    }

    override fun getSats(): Sats = sats
    override fun getMånedsberegninger(): List<Månedsberegning> = beregning.values.toList()
    override fun getFradrag(): List<Fradrag> = fradrag

    override fun getPeriode(): Periode = periode
}
