package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.behandling.BehandlingsinformasjonJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import java.time.format.DateTimeFormatter

internal fun Søknadsbehandling.toJson(): BehandlingJson {
    return when (val saksbehandling = this) {
        is Søknadsbehandling.Vilkårsvurdert -> BehandlingJson(
            id = saksbehandling.id.toString(),
            opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
            sakId = saksbehandling.sakId,
            søknad = saksbehandling.søknad.toJson(),
            behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
            status = saksbehandling.status.toString(),
            attestering = null,
            saksbehandler = null,
            beregning = null,
            simulering = null,
        )
        is Søknadsbehandling.Beregnet -> {
            BehandlingJson(
                id = saksbehandling.id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
                sakId = saksbehandling.sakId,
                søknad = saksbehandling.søknad.toJson(),
                behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
                status = saksbehandling.status.toString(),
                attestering = null,
                saksbehandler = null,
                beregning = saksbehandling.beregning.toJson(),
                simulering = null
            )
        }
        is Søknadsbehandling.Simulert -> {
            BehandlingJson(
                id = saksbehandling.id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
                sakId = saksbehandling.sakId,
                søknad = saksbehandling.søknad.toJson(),
                behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
                status = saksbehandling.status.toString(),
                attestering = null,
                saksbehandler = null,
                beregning = saksbehandling.beregning.toJson(),
                simulering = saksbehandling.simulering.toJson()
            )
        }
        is Søknadsbehandling.TilAttestering.Innvilget -> {
            BehandlingJson(
                id = saksbehandling.id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
                sakId = saksbehandling.sakId,
                søknad = saksbehandling.søknad.toJson(),
                behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
                status = saksbehandling.status.toString(),
                attestering = null,
                saksbehandler = saksbehandling.saksbehandler.toString(),
                beregning = saksbehandling.beregning.toJson(),
                simulering = saksbehandling.simulering.toJson()
            )
        }
        is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> {
            BehandlingJson(
                id = saksbehandling.id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
                sakId = saksbehandling.sakId,
                søknad = saksbehandling.søknad.toJson(),
                behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
                status = saksbehandling.status.toString(),
                attestering = null,
                saksbehandler = saksbehandling.saksbehandler.toString(),
                beregning = saksbehandling.beregning.toJson(),
                simulering = null
            )
        }
        is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = saksbehandling.id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(saksbehandling.opprettet),
                sakId = saksbehandling.sakId,
                søknad = saksbehandling.søknad.toJson(),
                behandlingsinformasjon = saksbehandling.behandlingsinformasjon.toJson(),
                status = saksbehandling.status.toString(),
                attestering = null,
                saksbehandler = saksbehandling.saksbehandler.toString(),
                beregning = null,
                simulering = null
            )
        }
        is Søknadsbehandling.Underkjent.Innvilget -> {
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
                beregning = saksbehandling.beregning.toJson(),
                simulering = saksbehandling.simulering.toJson()
            )
        }
        is Søknadsbehandling.Underkjent.Avslag.UtenBeregning -> {
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
                beregning = null,
                simulering = null
            )
        }
        is Søknadsbehandling.Underkjent.Avslag.MedBeregning -> {
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
                beregning = saksbehandling.beregning.toJson(),
                simulering = null
            )
        }
        is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
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
                beregning = saksbehandling.beregning.toJson(),
                simulering = null
            )
        }
        is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
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
                beregning = null,
                simulering = null
            )
        }
        is Søknadsbehandling.Iverksatt.Innvilget -> {
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
                beregning = saksbehandling.beregning.toJson(),
                simulering = saksbehandling.simulering.toJson()
            )
        }
    }
}

internal fun HttpStatusCode.jsonBody(søknadsbehandling: Søknadsbehandling): Resultat {
    return Resultat.json(this, serialize(søknadsbehandling.toJson()))
}
