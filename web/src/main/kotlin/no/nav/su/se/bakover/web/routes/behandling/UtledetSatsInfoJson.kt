package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.behandling.Behandling
import java.time.LocalDate

internal data class UtledetSatsInfoJson(
    val satsBeløp: Int?
)

internal fun Behandling.toUtledetSatsInfoJson() = UtledetSatsInfoJson(
    satsBeløp = getUtledetSatsBeløp(LocalDate.now())
)
