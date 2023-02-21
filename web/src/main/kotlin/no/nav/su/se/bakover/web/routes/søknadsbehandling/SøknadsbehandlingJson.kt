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
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AldersvurderingJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SøkandsbehandlingStatusJson.Companion.status
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.OppdaterStønadsperiodeRequest.Companion.toJson
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
            status = status().toString(),
            attesteringer = attesteringer.toJson(),
            saksbehandler = saksbehandler.toString(),
            beregning = null,
            simulering = null,
            stønadsperiode = stønadsperiode?.toJson(),
            grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
            fritekstTilBrev = fritekstTilBrev,
            erLukket = false,
            simuleringForAvkortingsvarsel = avkorting.toJson(),
            sakstype = sakstype.toJson(),
            aldersvurdering = this.aldersvurdering?.toJson(),
        )

        is Søknadsbehandling.Beregnet -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status().toString(),
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
                aldersvurdering = this.aldersvurdering.toJson(),
            )
        }

        is Søknadsbehandling.Simulert -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status().toString(),
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
                aldersvurdering = this.aldersvurdering.toJson(),
            )
        }

        is Søknadsbehandling.TilAttestering.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status().toString(),
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
                aldersvurdering = this.aldersvurdering.toJson(),
            )
        }

        is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status().toString(),
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
                aldersvurdering = this.aldersvurdering.toJson(),
            )
        }

        is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status().toString(),
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
                aldersvurdering = this.aldersvurdering.toJson(),
            )
        }

        is Søknadsbehandling.Underkjent.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status().toString(),
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
                aldersvurdering = this.aldersvurdering.toJson(),
            )
        }

        is Søknadsbehandling.Underkjent.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status().toString(),
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
                aldersvurdering = this.aldersvurdering.toJson(),
            )
        }

        is Søknadsbehandling.Underkjent.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status().toString(),
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
                aldersvurdering = this.aldersvurdering.toJson(),
            )
        }

        is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status().toString(),
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
                aldersvurdering = this.aldersvurdering.toJson(),
            )
        }

        is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status().toString(),
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
                aldersvurdering = this.aldersvurdering.toJson(),
            )
        }

        is Søknadsbehandling.Iverksatt.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                sakId = sakId,
                søknad = søknad.toJson(),
                status = status().toString(),
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
                aldersvurdering = this.aldersvurdering.toJson(),
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

internal enum class SøkandsbehandlingStatusJson {
    OPPRETTET,
    VILKÅRSVURDERT_INNVILGET,
    VILKÅRSVURDERT_AVSLAG,
    BEREGNET_INNVILGET,
    BEREGNET_AVSLAG,
    SIMULERT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_AVSLAG,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_AVSLAG,
    IVERKSATT_INNVILGET,
    IVERKSATT_AVSLAG,
    ;

    companion object {
        fun Søknadsbehandling.status(): SøkandsbehandlingStatusJson {
            return when (this) {
                is Søknadsbehandling.Beregnet.Avslag -> BEREGNET_AVSLAG
                is Søknadsbehandling.Beregnet.Innvilget -> BEREGNET_INNVILGET
                is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> IVERKSATT_AVSLAG
                is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> IVERKSATT_AVSLAG
                is Søknadsbehandling.Iverksatt.Innvilget -> IVERKSATT_INNVILGET
                is LukketSøknadsbehandling -> underliggendeSøknadsbehandling.status()
                is Søknadsbehandling.Simulert -> SIMULERT
                is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> TIL_ATTESTERING_AVSLAG
                is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> TIL_ATTESTERING_AVSLAG
                is Søknadsbehandling.TilAttestering.Innvilget -> TIL_ATTESTERING_INNVILGET
                is Søknadsbehandling.Underkjent.Avslag.MedBeregning -> UNDERKJENT_AVSLAG
                is Søknadsbehandling.Underkjent.Avslag.UtenBeregning -> UNDERKJENT_AVSLAG
                is Søknadsbehandling.Underkjent.Innvilget -> UNDERKJENT_INNVILGET
                is Søknadsbehandling.Vilkårsvurdert.Avslag -> VILKÅRSVURDERT_AVSLAG
                is Søknadsbehandling.Vilkårsvurdert.Innvilget -> VILKÅRSVURDERT_INNVILGET
                is Søknadsbehandling.Vilkårsvurdert.Uavklart -> OPPRETTET
            }
        }
    }
}
