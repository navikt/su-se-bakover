package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.periode.Periode

data class Behandlingsperiode(
    val periode: Periode,
    val begrunnelse: String = "",
)
