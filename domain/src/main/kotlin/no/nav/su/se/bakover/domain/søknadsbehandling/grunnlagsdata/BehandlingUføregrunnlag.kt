package no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata

import no.nav.su.se.bakover.common.periode.Periode

/**
 * @throws IllegalArgumentException hvis forventetInntekt er negativ
 */
data class BehandlingUføregrunnlag(
    val periode: Periode,
    val uføregrad: Uføregrad,
    /** Kan ikke være negativ. */
    val forventetInntekt: Int,
) {
    init {
        if (forventetInntekt < 0) throw IllegalArgumentException("forventetInntekt kan ikke være mindre enn 0")
    }
}
