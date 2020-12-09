package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype

internal data class Beregningsgrunnlag(
    val periode: Periode,
    private val forventetInntektPerÅr: Double,
    private val fradragFraSaksbehandler: List<Fradrag>
) {
    val fradrag: List<Fradrag> = fradragFraSaksbehandler.plus(
        FradragFactory.ny(
            type = Fradragstype.ForventetInntekt,
            månedsbeløp = forventetInntektPerÅr / 12.0,
            periode = periode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER
        )
    )
}
