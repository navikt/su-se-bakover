package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

val søknadsbehandlingId: UUID = UUID.randomUUID()

val behandlingsinformasjonAlleVilkårUavklart = Behandlingsinformasjon
    .lagTomBehandlingsinformasjon()

val behandlingsinformasjonAlleVilkårInnvilget = Behandlingsinformasjon
    .lagTomBehandlingsinformasjon()
    .withAlleVilkårOppfylt()

/**
 * Skal tilsvare en ny søknadsbehandling.
 * TODO jah: Vi bør kunne gjøre dette via NySøknadsbehandling og en funksjon som tar inn saksnummer og gir oss Søknadsbehandling.Vilkårsvurdert.Uavklart
 */
fun søknadsbehandlingVilkårsvurdertUavklart(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
): Søknadsbehandling.Vilkårsvurdert.Uavklart {
    return Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = søknadsbehandlingId,
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnr,
        søknad = journalførtSøknadMedOppgave,
        oppgaveId = oppgaveIdSøknad,
        behandlingsinformasjon = behandlingsinformasjonAlleVilkårUavklart,
        fnr = fnr,
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.EMPTY,
        vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        attesteringer = Attesteringshistorikk.empty()
    )
}

fun søknadsbehandlingVilkårsvurdertInnvilget(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
): Søknadsbehandling.Vilkårsvurdert.Innvilget {
    return (
        søknadsbehandlingVilkårsvurdertUavklart(saksnr = saksnr, stønadsperiode = stønadsperiode).tilVilkårsvurdert(
            behandlingsinformasjon,
        ) as Søknadsbehandling.Vilkårsvurdert.Innvilget
        ).copy(
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    )
}

fun søknadsbehandlingBeregnetInnvilget(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
): Søknadsbehandling.Beregnet.Innvilget {
    return søknadsbehandlingVilkårsvurdertInnvilget(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).tilBeregnet(beregning) as Søknadsbehandling.Beregnet.Innvilget
}

fun søknadsbehandlingSimulert(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
): Søknadsbehandling.Simulert {
    return søknadsbehandlingBeregnetInnvilget(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
    ).tilSimulert(
        simulering = simuleringNy(),
    )
}

fun søknadsbehandlingTilAttesteringInnvilget(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
): Søknadsbehandling.TilAttestering.Innvilget {
    return søknadsbehandlingSimulert(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
    ).tilAttestering(
        saksbehandler = saksbehandler,
        fritekstTilBrev = "",
    )
}

fun søknadsbehandlingUnderkjentInnvilget(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
    attestering: Attestering = attesteringUnderkjent,
): Søknadsbehandling.Underkjent.Innvilget {
    return søknadsbehandlingTilAttesteringInnvilget(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
    ).tilUnderkjent(
        attestering = attestering,
    )
}

fun søknadsbehandlingIverksattInnvilget(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
): Søknadsbehandling.Iverksatt.Innvilget {
    return søknadsbehandlingTilAttesteringInnvilget(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
    ).tilIverksatt(
        attestering = attesteringIverksatt,
    )
}
