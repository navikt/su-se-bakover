package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype

internal data class Beregningsgrunnlag(
    val beregningsperiode: Periode,
    private val fradragFraSaksbehandler: List<Fradrag>,
    private val forventetInntektPrÅr: Double
) {
    val fradrag: List<Fradrag> = oppjusterFradragForAntallMåneder()
        .plus(lagFradragForForventetInntekt())

    /**
     * Juster fradrag ved å kalkulere et nytt totalbeløp for perioden som er oppgitt på fradraget.
     * Det nye beløpet tilsvarer opprinnelig beløp (månedsbeløp) * antall måneder i fradragsperioden.
     * Eks:
     * fradrag: 01.01.20-31.12.20 beløp (måned): 10.000 -> fradrag: 01.01.20-31.12.20 beløp: (12 * 10.000) = 120.000
     * fradrag: 01.01.20-31.01.20 beløp (måned): 10.000 -> fradrag: 01.01.20-31.01.20 beløp: (01 * 10.000) =  10.000
     */
    private fun oppjusterFradragForAntallMåneder(): List<Fradrag> =
        fradragFraSaksbehandler.map {
            FradragFactory.ny(
                type = it.getFradragstype(),
                beløp = it.getTotaltFradrag() * it.getPeriode().getAntallMåneder(),
                periode = it.getPeriode(),
                utenlandskInntekt = it.getUtenlandskInntekt(),
                tilhører = it.getTilhører()
            )
        }

    /**
     * Juster fradrag for forventet inntekt i forhold til hvor mange måneder som skal beregnes.
     * Kalkulerer et totalt fradrag for hele beregningsperioden tilsvarende forventet inntekt som månedsbeløp * antall måneder i beregningsperioden.
     * Eks: forventetInntektPrÅr = 120.000, forventetInntektPrMnd = 10.000
     * for beregningsperiode 01.01.20-31.12.20 -> fradrag: 01.01.20-31.12.20 beløp: (12 * 10.000) = 120.000
     * for beregningsperiode 01.01.20-31.01.20 -> fradrag: 01.01.20-31.01.20 beløp: (01 * 10.000) =  10.000
     */
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
