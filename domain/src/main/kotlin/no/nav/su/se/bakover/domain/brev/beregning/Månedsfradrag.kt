package no.nav.su.se.bakover.domain.brev.beregning

import beregning.domain.fradrag.UtenlandskInntekt

data class Månedsfradrag(
    val type: String,
    val beløp: Int,
    val utenlandskInntekt: UtenlandskInntekt?,
)
