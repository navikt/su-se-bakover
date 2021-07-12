package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger

fun vedtakSøknadsbehandlingIverksattInnvilget(
    saksnr: Saksnummer = saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
    utbetalingId: UUID30 = UUID30.randomUUID(),
) = Vedtak.fromSøknadsbehandling(
    søknadsbehandling = søknadsbehandlingIverksattInnvilget(
        saksnr = saksnr,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
    ),
    utbetalingId = utbetalingId,
    clock = fixedClock,
)

fun vedtakRevurderingIverksattInnvilget(
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    utbetalingId: UUID30 = UUID30.randomUUID(),
    tilRevurdering: VedtakSomKanRevurderes
) = Vedtak.from(
    revurdering = IverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        tilRevurdering = tilRevurdering
    ),
    utbetalingId = utbetalingId,
    clock = fixedClock,
)
