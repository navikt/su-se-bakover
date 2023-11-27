package no.nav.su.se.bakover.domain.brev.beregning

import behandling.domain.beregning.fradrag.UtenlandskInntekt

data class Månedsfradrag(
    val type: String,
    val beløp: Int,
    val utenlandskInntekt: UtenlandskInntekt?,
)
