package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype

internal data class Beregningsgrunnlag(
    val beregningsperiode: Periode,
    private val fraSaksbehandler: List<Fradrag>,
    private val forventetInntektPrÅr: Double
) {
    val fradrag: List<Fradrag> = oppjusterFradragForAntallMåneder()
        .plus(lagFradragForForventetInntekt())

    private fun oppjusterFradragForAntallMåneder(): List<Fradrag> =
        fraSaksbehandler.map {
            FradragFactory.ny(
                type = it.getFradragstype(),
                beløp = it.getTotaltFradrag() * it.getPeriode().getAntallMåneder(),
                periode = it.getPeriode(),
                utenlandskInntekt = it.getUtenlandskInntekt(),
                tilhører = it.getTilhører()
            )
        }

    private fun lagFradragForForventetInntekt(): Fradrag {
        val prMnd = forventetInntektPrÅr / 12.0
        val totaltForBeregningsperiode = prMnd * beregningsperiode.getAntallMåneder()
        return FradragFactory.ny(
            type = Fradragstype.ForventetInntekt,
            beløp = totaltForBeregningsperiode,
            periode = beregningsperiode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER
        )
    }
}
