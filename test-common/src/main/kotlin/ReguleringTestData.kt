package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

fun innvilgetSøknadsbehandlingMedÅpenRegulering(
    regulerFraOgMed: LocalDate,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(stønadsperiode.periode),
    clock: Clock = fixedClock,
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Regulering.OpprettetRegulering> {
    val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        clock = clock,
        avkorting = avkorting,
    )
    val sak = sakOgVedtak.first
    val søknadsbehandling = sak.søknadsbehandlinger.single() as Søknadsbehandling.Iverksatt.Innvilget
    val periode = Periode.create(regulerFraOgMed, søknadsbehandling.periode.tilOgMed)

    val gjeldendeVedtaksdata = sak.hentGjeldendeVilkårOgGrunnlag(periode, clock)
    val regulering = Regulering.OpprettetRegulering(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(clock),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        periode = periode,
        grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata,
        beregning = søknadsbehandling.beregning,
        simulering = søknadsbehandling.simulering,
        saksbehandler = saksbehandler,
        reguleringType = ReguleringType.AUTOMATISK,
    )

    return Pair(
        sak.copy(
            reguleringer = listOf(regulering),
        ),
        regulering,
    )
}
