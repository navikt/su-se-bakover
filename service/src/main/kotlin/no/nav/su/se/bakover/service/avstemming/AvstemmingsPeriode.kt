package no.nav.su.se.bakover.service.avstemming

import no.nav.su.se.bakover.common.tid.Tidspunkt

data class AvstemmingsPeriode(
    val fraOgMed: Tidspunkt,
    val tilOgMed: Tidspunkt,
)
