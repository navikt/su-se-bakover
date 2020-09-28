package no.nav.su.se.bakover.service.avstemming

import java.time.Instant

data class AvstemmingsPeriode(
    val fom: Instant,
    val tom: Instant
)
