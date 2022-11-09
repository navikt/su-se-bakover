package no.nav.su.se.bakover.domain.revurdering.stans

import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering

data class StansAvYtelseITransaksjonResponse(
    val revurdering: StansAvYtelseRevurdering.SimulertStansAvYtelse,
    val sendStatistikkCallback: () -> Unit,
)
