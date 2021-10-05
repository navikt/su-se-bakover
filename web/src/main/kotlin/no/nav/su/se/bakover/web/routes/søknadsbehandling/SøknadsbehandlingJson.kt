package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.grunnlag.throwIfMultiple
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson.Companion.create
import no.nav.su.se.bakover.web.routes.søknad.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingsinformasjonJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.StønadsperiodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import java.time.format.DateTimeFormatter

internal fun Søknadsbehandling.toJson(): BehandlingJson {
    return when (this) {
        is Søknadsbehandling.Vilkårsvurdert -> BehandlingJson(
            id = id.toString(),
            opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
            sakId = sakId,
            søknad = søknad.toJson(),
            behandlingsinformasjon = behandlingsinformasjon.toJson(),
            status = status.toString(),
            attesteringer = attesteringer.toJson(),
            saksbehandler = null,
            beregning = null,
            simulering = null,
            stønadsperiode = stønadsperiode?.toJson(),
            grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
            fritekstTilBrev = fritekstTilBrev,
        )
        is Søknadsbehandling.Beregnet -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                behandlingsinformasjon = behandlingsinformasjon.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = null,
                beregning = beregning.toJson(),
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
                fritekstTilBrev = fritekstTilBrev,
            )
        }
        is Søknadsbehandling.Simulert -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                behandlingsinformasjon = behandlingsinformasjon.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = null,
                beregning = beregning.toJson(),
                simulering = simulering.toJson(),
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
                fritekstTilBrev = fritekstTilBrev,
            )
        }
        is Søknadsbehandling.TilAttestering.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                behandlingsinformasjon = behandlingsinformasjon.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = beregning.toJson(),
                simulering = simulering.toJson(),
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
                fritekstTilBrev = fritekstTilBrev,
            )
        }
        is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                behandlingsinformasjon = behandlingsinformasjon.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = beregning.toJson(),
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
                fritekstTilBrev = fritekstTilBrev,
            )
        }
        is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                behandlingsinformasjon = behandlingsinformasjon.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = null,
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
                fritekstTilBrev = fritekstTilBrev,
            )
        }
        is Søknadsbehandling.Underkjent.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                behandlingsinformasjon = behandlingsinformasjon.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.hentAttesteringer().map {
                    when (it) {
                        is Attestering.Iverksatt -> AttesteringJson(
                            attestant = it.attestant.navIdent,
                            opprettet = it.opprettet,
                            underkjennelse = null,
                        )
                        is Attestering.Underkjent -> AttesteringJson(
                            attestant = it.attestant.navIdent,
                            opprettet = it.opprettet,
                            underkjennelse = UnderkjennelseJson(
                                grunn = it.grunn.toString(),
                                kommentar = it.kommentar,
                            ),
                        )
                    }
                },
                saksbehandler = saksbehandler.toString(),
                beregning = beregning.toJson(),
                simulering = simulering.toJson(),
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
                fritekstTilBrev = fritekstTilBrev,
            )
        }
        is Søknadsbehandling.Underkjent.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                behandlingsinformasjon = behandlingsinformasjon.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = null,
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
                fritekstTilBrev = fritekstTilBrev,
            )
        }
        is Søknadsbehandling.Underkjent.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                behandlingsinformasjon = behandlingsinformasjon.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = beregning.toJson(),
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
                fritekstTilBrev = fritekstTilBrev,
            )
        }
        is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                behandlingsinformasjon = behandlingsinformasjon.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = beregning.toJson(),
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
                fritekstTilBrev = fritekstTilBrev,
            )
        }
        is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                behandlingsinformasjon = behandlingsinformasjon.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = null,
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
                fritekstTilBrev = fritekstTilBrev,
            )
        }
        is Søknadsbehandling.Iverksatt.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                behandlingsinformasjon = behandlingsinformasjon.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = beregning.toJson(),
                simulering = simulering.toJson(),
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger),
                fritekstTilBrev = fritekstTilBrev,
            )
        }
    }.also {
        // TODO jah: Vi skal ikke pre-utfylle Bosituasjon for revurdering med mer enn ett element.
        //  vi ønsker å gjøre denne sjekken backend for å ha bedre kontroll + oversikt (logger)
        grunnlagsdata.bosituasjon.throwIfMultiple()
    }
}

internal fun HttpStatusCode.jsonBody(søknadsbehandling: Søknadsbehandling): Resultat {
    return Resultat.json(this, serialize(søknadsbehandling.toJson()))
}
