package no.nav.su.se.bakover.domain.brev.beregning

import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt

data class Månedsfradrag(
    val type: String,
    val beløp: Int,
    val utenlandskInntekt: UtenlandskInntekt?,
)
