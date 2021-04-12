package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import java.util.UUID

internal data class BeregningMedFradragBeregnetMånedsvis(
    private val id: UUID = UUID.randomUUID(),
    private val opprettet: Tidspunkt = Tidspunkt.now(),
    private val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>,
    private val fradragStrategy: FradragStrategy,
    private val begrunnelse: String?
) : Beregning {
    private val beregning = beregn()

    init {
        require(fradrag.all { periode inneholder it.getPeriode() })
    }

    override fun getId(): UUID = id

    override fun getOpprettet(): Tidspunkt = opprettet

    override fun getSumYtelse() = beregning.values
        .sumBy { it.getSumYtelse() }

    override fun getSumFradrag() = beregning.values
        .sumByDouble { it.getSumFradrag() }

    override fun getFradragStrategyName(): FradragStrategyName = fradragStrategy.getName()

    private fun beregn(): Map<Periode, Månedsberegning> {
        val perioder = periode.tilMånedsperioder()

        val beregnetPeriodisertFradrag = fradragStrategy.beregn(fradrag, periode)

        return perioder.map {
            it to MånedsberegningFactory.ny(
                periode = it,
                sats = sats,
                fradrag = beregnetPeriodisertFradrag[it] ?: emptyList()
            ).let { månedsberegning ->
                when (månedsberegning.ytelseStørreEnn0MenMindreEnnToProsentAvHøySats()) {
                    true -> MånedsberegningFactory.ny(
                        periode = månedsberegning.getPeriode(),
                        sats = sats,
                        fradrag = månedsberegning.getFradrag()
                            .plus(månedsberegning.lagFradragForBeløpUnderMinstegrense())
                    )
                    false -> månedsberegning
                }
            }
        }.toMap()
    }

    private fun Månedsberegning.lagFradragForBeløpUnderMinstegrense() = FradragFactory.periodiser(
        FradragFactory.ny(
            type = Fradragstype.UnderMinstenivå,
            månedsbeløp = getSumYtelse().toDouble(),
            periode = getPeriode(),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER
        )
    )

    private fun Månedsberegning.ytelseStørreEnn0MenMindreEnnToProsentAvHøySats() =
        getSumYtelse() > 0 && getSumYtelse() < Sats.toProsentAvHøy(getPeriode())

    override fun getSats(): Sats = sats
    override fun getMånedsberegninger(): List<Månedsberegning> = beregning.values.toList()
    override fun getFradrag(): List<Fradrag> = fradrag
    override fun getBegrunnelse(): String? = begrunnelse

    override fun getPeriode(): Periode = periode
}
