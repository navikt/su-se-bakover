package no.nav.su.se.bakover.test

import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

fun opprettetRegulering(
    id: UUID = UUID.randomUUID(),
    sakId: UUID = UUID.randomUUID(),
    reguleringsperiode: Periode = stønadsperiode2021.periode,
    saksnummer: Saksnummer = Saksnummer(2021),
    opprettet: Tidspunkt = fixedTidspunkt,
    fnr: Fnr = Fnr.generer(),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
        grunnlagsdataEnsligUtenFradrag(periode = reguleringsperiode),
        vilkårsvurderingerRevurderingInnvilget(periode = reguleringsperiode),
    ),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler(saksbehandlerNavn),
    reguleringstype: Reguleringstype = Reguleringstype.MANUELL(emptySet()),
    sakstype: Sakstype = Sakstype.UFØRE,
) = Regulering.OpprettetRegulering(
    id = id,
    opprettet = opprettet,
    sakId = sakId,
    saksnummer = saksnummer,
    fnr = fnr,
    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
    periode = reguleringsperiode,
    beregning = null,
    simulering = null,
    saksbehandler = saksbehandler,
    reguleringstype = reguleringstype,
    sakstype = sakstype,
)

fun iverksattAutomatiskRegulering(
    id: UUID = UUID.randomUUID(),
    sakId: UUID = UUID.randomUUID(),
    reguleringsperiode: Periode = stønadsperiode2021.periode,
    saksnummer: Saksnummer = Saksnummer(2021),
    opprettet: Tidspunkt = fixedTidspunkt,
    fnr: Fnr = Fnr.generer(),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
        grunnlagsdataEnsligUtenFradrag(reguleringsperiode),
        vilkårsvurderingerRevurderingInnvilget(reguleringsperiode),
    ),
    reguleringstype: Reguleringstype = Reguleringstype.AUTOMATISK,
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
    clock: Clock = fixedClock,
    sakstype: Sakstype = Sakstype.UFØRE,
): Regulering.IverksattRegulering = opprettetRegulering(
    id = id,
    sakId = sakId,
    reguleringsperiode = reguleringsperiode,
    saksnummer = saksnummer,
    opprettet = opprettet,
    fnr = fnr,
    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
    reguleringstype = reguleringstype,
    saksbehandler = saksbehandler,
    sakstype = sakstype,
)
    .beregn(satsFactoryTestPåDato(), null, clock).getOrFail()
    .simuler(
        simuler = { _, _ ->
            simulering().right() // TODO bare tull, refaktorer vekk hele funksjonen og gjør koblinger mot sak/revurdering
        },
    ).getOrFail()
    .tilIverksatt()

fun innvilgetSøknadsbehandlingMedÅpenRegulering(
    regulerFraOgMed: LocalDate,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    clock: Clock = TikkendeKlokke(),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Regulering.OpprettetRegulering> {
    val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        clock = clock,
        avkorting = avkorting,
    )
    val sak = sakOgVedtak.first
    val regulering = sak.opprettEllerOppdaterRegulering(regulerFraOgMed, clock).getOrFail()

    return Pair(
        sak.copy(
            reguleringer = listOf(regulering),
        ),
        regulering,
    )
}

fun stansetSøknadsbehandlingMedÅpenRegulering(
    regulerFraOgMed: LocalDate,
    clock: Clock = fixedClock,
): Pair<Sak, Regulering.OpprettetRegulering> {
    val sakOgVedtak = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        clock = clock,
    )
    val sak = sakOgVedtak.first
    val regulering = sak.opprettEllerOppdaterRegulering(
        startDato = regulerFraOgMed,
        clock = clock,
    ).getOrFail()

    return Pair(
        sak.copy(
            reguleringer = listOf(regulering),
        ),
        regulering,
    )
}

fun innvilgetSøknadsbehandlingMedIverksattRegulering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
        stønadsperiode.periode,
    ),
    clock: Clock = TikkendeKlokke(),
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, Regulering.IverksattRegulering> {
    val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        clock = clock,
        avkorting = avkorting,
    )
    val sak = sakOgVedtak.first
    val regulering = iverksattAutomatiskRegulering()

    return Pair(
        sak.copy(
            reguleringer = listOf(regulering),
        ),
        regulering,
    )
}

fun avsluttetRegulering(
    id: UUID = UUID.randomUUID(),
    sakId: UUID = UUID.randomUUID(),
    reguleringsperiode: Periode = stønadsperiode2021.periode,
    saksnummer: Saksnummer = Saksnummer(2021),
    opprettet: Tidspunkt = fixedTidspunkt,
    fnr: Fnr = Fnr.generer(),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
        grunnlagsdataEnsligUtenFradrag(periode = reguleringsperiode),
        vilkårsvurderingerRevurderingInnvilget(periode = reguleringsperiode),
    ),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler(saksbehandlerNavn),
    reguleringstype: Reguleringstype = Reguleringstype.MANUELL(emptySet()),
    sakstype: Sakstype = Sakstype.UFØRE,
    avsluttetTidspunkt: Clock = enUkeEtterFixedClock,
): Regulering.AvsluttetRegulering {
    return opprettetRegulering(
        id = id,
        sakId = sakId,
        reguleringsperiode = reguleringsperiode,
        saksnummer = saksnummer,
        opprettet = opprettet,
        fnr = fnr,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        saksbehandler = saksbehandler,
        reguleringstype = reguleringstype,
        sakstype = sakstype,
    ).avslutt(avsluttetTidspunkt)
}
