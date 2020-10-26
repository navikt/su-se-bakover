package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.behandling.BehandlingsinformasjonJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.hendelse.HendelseJson
import no.nav.su.se.bakover.web.routes.hendelse.toJson
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
    val simulering: SimuleringJson?,
    val opprettet: String,
    val attestant: String?,
    val saksbehandler: String?,
    val sakId: UUID,
    val hendelser: List<HendelseJson>?
)

internal fun Behandling.toJson() = BehandlingJson(
    id = id.toString(),
    behandlingsinformasjon = behandlingsinformasjon().toJson(),
    søknad = søknad.toJson(),
    beregning = beregning()?.toJson(),
    status = status().toString(),
    simulering = simulering()?.toJson(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    attestant = attestant()?.navIdent,
    saksbehandler = saksbehandler()?.navIdent,
    sakId = sakId,
    hendelser = hendelser().toJson(),
)

internal fun HttpStatusCode.jsonBody(behandling: Behandling) =
    Resultat.json(this, serialize(behandling.toJson()))
