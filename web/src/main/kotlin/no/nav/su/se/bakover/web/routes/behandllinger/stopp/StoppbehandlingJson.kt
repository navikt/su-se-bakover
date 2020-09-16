package no.nav.su.se.bakover.web.routes.behandllinger.stopp

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.behandlinger.stopp.Stoppbehandling
import no.nav.su.se.bakover.web.Resultat
import java.time.Instant

data class StoppbehandlingJson(
    val id: String,
    val opprettet: Instant,
    val sakId: String,
    val status: String
)

internal fun Stoppbehandling.toResultat(statusCode: HttpStatusCode) =
    Resultat.json(statusCode, serialize(this.toJson()))

fun Stoppbehandling.toJson() = when (this) {
    is Stoppbehandling.Opprettet -> this.toJson()
    is Stoppbehandling.Simulert -> TODO()
    is Stoppbehandling.TilAttestering -> TODO()
    is Stoppbehandling.Iverksatt -> TODO()
}

fun Stoppbehandling.Opprettet.toJson() = StoppbehandlingJson(
    id = id.toString(),
    opprettet = opprettet,
    sakId = sakId.toString(),
    status = status.name
)
