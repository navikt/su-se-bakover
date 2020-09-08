package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.behandling.BehandlingsinformasjonJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class BehandlingJson(
    val id: String,
    val behandlingsinformasjon: BehandlingsinformasjonJson,
    val søknad: SøknadJson,
    val beregning: BeregningJson?,
    val status: String,
    val utbetaling: UtbetalingJson?,
    val opprettet: String,
    val attestant: String?,
    val sakId: UUID
)

internal fun Behandling.toJson() = BehandlingJson(
    id = id.toString(),
    behandlingsinformasjon = behandlingsinformasjon().toJson(),
    søknad = søknad.toJson(),
    beregning = beregning()?.toJson(),
    status = status().toString(),
    utbetaling = utbetaling()?.toJson(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    attestant = attestant()?.id,
    sakId = sakId
)

internal fun HttpStatusCode.jsonBody(behandling: Behandling) =
    Resultat.json(this, serialize(behandling.toJson()))
