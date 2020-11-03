package no.nav.su.se.bakover.domain.beregning.beregning

import no.nav.su.se.bakover.domain.beregning.fradrag.IFradrag
import java.time.LocalDate

data class Beregningsgrunnlag(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val fradrag: List<IFradrag>,
    val forventetInntekt: Int
)
