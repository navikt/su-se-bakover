package no.nav.su.se.bakover.web.routes.behandling

import AttesteringJson
import UnderkjennelseJson
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
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
    val attestering: AttesteringJson?,
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
    attestering = attestering()?.let {
        when (val attestering = attestering() as Attestering) {
            is Attestering.Iverksatt -> AttesteringJson(
                attestant = attestering.attestant.navIdent,
                underkjennelse = null
            )
            is Attestering.Underkjent -> AttesteringJson(
                attestant = attestering.attestant.navIdent,
                underkjennelse = UnderkjennelseJson(
                    grunn = attestering.underkjennelse.grunn.toString(),
                    kommentar = attestering.underkjennelse.kommentar
                )
            )
        }
    },
    saksbehandler = saksbehandler()?.navIdent,
    sakId = sakId,
    hendelser = hendelser().toJson(),
)

internal fun HttpStatusCode.jsonBody(behandling: Behandling) =
    Resultat.json(this, serialize(behandling.toJson()))
