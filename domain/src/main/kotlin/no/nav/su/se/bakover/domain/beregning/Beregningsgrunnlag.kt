package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag

internal data class Beregningsgrunnlag(
    val periode: Periode,
    val fradrag: List<Fradrag>
)
