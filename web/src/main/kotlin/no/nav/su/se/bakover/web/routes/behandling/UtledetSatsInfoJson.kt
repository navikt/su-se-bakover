package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.web.Resultat


internal data class UtledetSatsInfoJson(
    val satsBeløp: Int?
)

internal fun Behandling.toUtledetSatsInfoJson() = UtledetSatsInfoJson(
    satsBeløp = getUtledetSatsBeløp()
)

internal fun HttpStatusCode.utledetSatsJsonBody(behandling: Behandling) =
    Resultat.json(this, serialize(behandling.toUtledetSatsInfoJson()))
