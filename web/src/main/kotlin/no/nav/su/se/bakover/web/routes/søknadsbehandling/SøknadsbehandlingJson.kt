package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson.Companion.create
import no.nav.su.se.bakover.web.routes.sak.toJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.StønadsperiodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import java.time.format.DateTimeFormatter

internal fun Søknadsbehandling.json(satsFactory: SatsFactory): String {
    return serialize(toJson(satsFactory))
}

internal fun Søknadsbehandling.toJson(satsFactory: SatsFactory): BehandlingJson {
    return when (this) {
        is Søknadsbehandling.Vilkårsvurdert -> BehandlingJson(
            id = id.toString(),
            opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
            sakId = sakId,
            søknad = søknad.toJson(),
            status = status.toString(),
            attesteringer = attesteringer.toJson(),
            saksbehandler = null,
            beregning = null,
            simulering = null,
            stønadsperiode = stønadsperiode?.toJson(),
            grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
            fritekstTilBrev = fritekstTilBrev,
            erLukket = false,
            simuleringForAvkortingsvarsel = avkorting.toJson(),
            sakstype = sakstype.toJson(),
        )
        is Søknadsbehandling.Beregnet -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = null,
                beregning = beregning.toJson(),
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                fritekstTilBrev = fritekstTilBrev,
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is Søknadsbehandling.Simulert -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = null,
                beregning = beregning.toJson(),
                simulering = simulering.toJson(),
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                fritekstTilBrev = fritekstTilBrev,
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is Søknadsbehandling.TilAttestering.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = beregning.toJson(),
                simulering = simulering.toJson(),
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                fritekstTilBrev = fritekstTilBrev,
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = beregning.toJson(),
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                fritekstTilBrev = fritekstTilBrev,
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = null,
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                fritekstTilBrev = fritekstTilBrev,
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is Søknadsbehandling.Underkjent.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.map {
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
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                fritekstTilBrev = fritekstTilBrev,
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is Søknadsbehandling.Underkjent.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = null,
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                fritekstTilBrev = fritekstTilBrev,
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is Søknadsbehandling.Underkjent.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = beregning.toJson(),
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                fritekstTilBrev = fritekstTilBrev,
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = beregning.toJson(),
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                fritekstTilBrev = fritekstTilBrev,
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = null,
                simulering = null,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                fritekstTilBrev = fritekstTilBrev,
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is Søknadsbehandling.Iverksatt.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status.toString(),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                beregning = beregning.toJson(),
                simulering = simulering.toJson(),
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                fritekstTilBrev = fritekstTilBrev,
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
            )
        }
        is LukketSøknadsbehandling -> {
            underliggendeSøknadsbehandling.toJson(satsFactory).copy(erLukket = true)
        }
    }
}

internal fun HttpStatusCode.jsonBody(søknadsbehandling: Søknadsbehandling, satsFactory: SatsFactory): Resultat {
    return Resultat.json(this, serialize(søknadsbehandling.toJson(satsFactory)))
}
