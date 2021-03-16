package no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import java.util.UUID

/**
 * @throws IllegalArgumentException hvis forventetInntekt er negativ
 */
data class Uføregrunnlag(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = Tidspunkt.now(),
    val periode: Periode,
    val uføregrad: Uføregrad,
    /** Kan ikke være negativ. */
    val forventetInntekt: Int,
) {
    init {
        if (forventetInntekt < 0) throw IllegalArgumentException("forventetInntekt kan ikke være mindre enn 0")
    }

    // TODO avoid copying id
    fun copy(
        periode: Periode = this.periode,
        opprettet: Tidspunkt = this.opprettet,
        uføregrad: Uføregrad = this.uføregrad,
        forventetInntekt: Int = this.forventetInntekt
    ) = Uføregrunnlag(
        UUID.randomUUID(), opprettet, periode, uføregrad, forventetInntekt
    )
}
