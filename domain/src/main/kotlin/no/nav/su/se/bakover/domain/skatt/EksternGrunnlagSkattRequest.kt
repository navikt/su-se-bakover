package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt

data class EksternGrunnlagSkattRequest(
    val søkers: Skattegrunnlag,
    val eps: Skattegrunnlag?,
) {
    fun tilHentet(): EksterneGrunnlagSkatt.Hentet {
        return EksterneGrunnlagSkatt.Hentet(søkers = søkers, eps = eps)
    }
}
