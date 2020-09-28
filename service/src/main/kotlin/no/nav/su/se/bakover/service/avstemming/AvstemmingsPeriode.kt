package no.nav.su.se.bakover.service.avstemming

import no.nav.su.se.bakover.common.Tidspunkt

data class AvstemmingsPeriode(
    val fom: Tidspunkt,
    val tom: Tidspunkt
)
