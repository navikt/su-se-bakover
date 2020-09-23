package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.Behandling

internal data class UtledetSatsInfoJson(
    val satsBeløp: Int?
)

internal fun Behandling.toUtledetSatsInfoJson() = UtledetSatsInfoJson(
    satsBeløp = getUtledetSatsBeløp()
)
