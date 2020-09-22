package no.nav.su.se.bakover.web.routes.behandlinger.stopp

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.behandlinger.stopp.Stoppbehandling
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson.Companion.toJson
import java.time.Instant

data class StoppbehandlingJson(
    val id: String,
    val opprettet: Instant,
    val sakId: String,
    val status: String,
    val utbetaling: UtbetalingJson,
    val stoppÅrsak: String,
    val saksbehandler: String
)

internal fun Stoppbehandling.toResultat(statusCode: HttpStatusCode) =
    Resultat.json(statusCode, serialize(this.toJson()))

fun Stoppbehandling.toJson() = when (this) {
    is Stoppbehandling.Simulert -> this.toJson()
    is Stoppbehandling.TilAttestering -> TODO()
    is Stoppbehandling.Iverksatt -> TODO()
}

fun Stoppbehandling.Simulert.toJson() = StoppbehandlingJson(
    id = id.toString(),
    opprettet = opprettet,
    sakId = sakId.toString(),
    status = status,
    utbetaling = utbetaling.toJson(),
    stoppÅrsak = stoppÅrsak,
    saksbehandler = saksbehandler.id
)
