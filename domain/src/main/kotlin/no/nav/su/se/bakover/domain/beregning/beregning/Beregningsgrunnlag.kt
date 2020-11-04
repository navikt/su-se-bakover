package no.nav.su.se.bakover.domain.beregning.beregning

import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.time.LocalDate

internal data class Beregningsgrunnlag(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val fradrag: List<Fradrag>,
    val forventetInntekt: Int
)
