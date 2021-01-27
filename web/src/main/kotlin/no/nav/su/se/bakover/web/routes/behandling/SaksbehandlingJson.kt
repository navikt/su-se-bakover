package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Saksbehandling
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.behandling.BehandlingsinformasjonJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import java.time.format.DateTimeFormatter

internal fun Saksbehandling.toJson(): BehandlingJson {
    val saksbehandling = this
    return when (saksbehandling) {
        is Saksbehandling.Søknadsbehandling -> {
            when (saksbehandling) {
                is Saksbehandling.Søknadsbehandling.Opprettet, is Saksbehandling.Søknadsbehandling.Vilkårsvurdert -> BehandlingJson(
                    id = saksbehandling.id.toString(),
                    opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
                    sakId = saksbehandling.sakId,
                    søknad = saksbehandling.søknad.toJson(),
                    behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
                    status = saksbehandling.status.toString(),
                    attestering = null,
                    saksbehandler = null,
                    hendelser = null,
                    beregning = null,
                    simulering = null
                )
                is Saksbehandling.Søknadsbehandling.Beregnet -> {
                    BehandlingJson(
                        id = saksbehandling.id.toString(),
                        opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
                        sakId = saksbehandling.sakId,
                        søknad = saksbehandling.søknad.toJson(),
                        behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
                        status = saksbehandling.status.toString(),
                        attestering = null,
                        saksbehandler = null,
                        hendelser = null,
                        beregning = saksbehandling.beregning.toJson(),
                        simulering = null
                    )
                }
                is Saksbehandling.Søknadsbehandling.Simulert -> {
                    BehandlingJson(
                        id = saksbehandling.id.toString(),
                        opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
                        sakId = saksbehandling.sakId,
                        søknad = saksbehandling.søknad.toJson(),
                        behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
                        status = saksbehandling.status.toString(),
                        attestering = null,
                        saksbehandler = null,
                        hendelser = null,
                        beregning = saksbehandling.beregning.toJson(),
                        simulering = saksbehandling.simulering.toJson()
                    )
                }
                is Saksbehandling.Søknadsbehandling.TilAttestering.Innvilget -> {
                    BehandlingJson(
                        id = saksbehandling.id.toString(),
                        opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
                        sakId = saksbehandling.sakId,
                        søknad = saksbehandling.søknad.toJson(),
                        behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
                        status = saksbehandling.status.toString(),
                        attestering = null,
                        saksbehandler = saksbehandling.saksbehandler.toString(),
                        hendelser = null,
                        beregning = saksbehandling.beregning.toJson(),
                        simulering = saksbehandling.simulering.toJson()
                    )
                }
                is Saksbehandling.Søknadsbehandling.Attestert.Underkjent -> {
                    BehandlingJson(
                        id = saksbehandling.id.toString(),
                        opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
                        sakId = saksbehandling.sakId,
                        søknad = saksbehandling.søknad.toJson(),
                        behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
                        status = saksbehandling.status.toString(),
                        attestering = saksbehandling.attestering.let {
                            when (it) {
                                is Attestering.Iverksatt -> AttesteringJson(
                                    attestant = it.attestant.navIdent,
                                    underkjennelse = null
                                )
                                is Attestering.Underkjent -> AttesteringJson(
                                    attestant = it.attestant.navIdent,
                                    underkjennelse = UnderkjennelseJson(
                                        grunn = it.grunn.toString(),
                                        kommentar = it.kommentar
                                    )
                                )
                            }
                        },
                        saksbehandler = saksbehandling.saksbehandler.toString(),
                        hendelser = null,
                        beregning = saksbehandling.beregning.toJson(),
                        simulering = saksbehandling.simulering.toJson()
                    )
                }
                is Saksbehandling.Søknadsbehandling.Attestert.Iverksatt.Innvilget -> {
                    BehandlingJson(
                        id = saksbehandling.id.toString(),
                        opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
                        sakId = saksbehandling.sakId,
                        søknad = saksbehandling.søknad.toJson(),
                        behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
                        status = saksbehandling.status.toString(),
                        attestering = saksbehandling.attestering.let {
                            when (it) {
                                is Attestering.Iverksatt -> AttesteringJson(
                                    attestant = it.attestant.navIdent,
                                    underkjennelse = null
                                )
                                is Attestering.Underkjent -> AttesteringJson(
                                    attestant = it.attestant.navIdent,
                                    underkjennelse = UnderkjennelseJson(
                                        grunn = it.grunn.toString(),
                                        kommentar = it.kommentar
                                    )
                                )
                            }
                        },
                        saksbehandler = saksbehandling.saksbehandler.toString(),
                        hendelser = null,
                        beregning = saksbehandling.beregning.toJson(),
                        simulering = saksbehandling.simulering.toJson()
                    )
                }
                else -> throw NotImplementedError()
            }
        }
        else -> throw NotImplementedError()
    }
}

internal fun HttpStatusCode.jsonBody(saksbehandling: Saksbehandling): Resultat {
    return Resultat.json(this, serialize(saksbehandling.toJson()))
}
