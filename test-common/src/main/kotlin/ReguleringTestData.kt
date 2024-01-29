package no.nav.su.se.bakover.test

import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.opprettEllerOppdaterRegulering
import no.nav.su.se.bakover.domain.sak.nyRegulering
import no.nav.su.se.bakover.test.utbetaling.simulertUtbetaling
import vilkår.common.domain.Vilkår
import vilkår.common.domain.grunnlag.Grunnlag
import økonomi.domain.simulering.Simuleringsresultat
import java.time.Clock
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
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler(SAKSBEHANDLER_NAVN),
    reguleringstype: Reguleringstype = Reguleringstype.MANUELL(emptySet()),
    sakstype: Sakstype = Sakstype.UFØRE,
) = OpprettetRegulering(
    // TODO jah: Her omgår vi mye domenelogikk. Bør bruke Regulering.opprettRegulering(...) som tar utgangspunkt i en sak/gjeldendeVedtak.
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
): IverksattRegulering {
    return opprettetRegulering(
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
                // TODO jah: Denne simuleringen henger ikke sammen med resten av dataene. Bør lage en sak og gå via APIene der. Se SøknadsbehandlingTestData f.eks.
                Simuleringsresultat.UtenForskjeller(simulertUtbetaling()).right()
            },
        ).getOrFail()
        .first
        .tilIverksatt()
}

fun innvilgetSøknadsbehandlingMedÅpenRegulering(
    regulerFraOgMed: Måned,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    clock: Clock = TikkendeKlokke(),
): Pair<Sak, OpprettetRegulering> {
    val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        clock = clock,
    )
    val sak = sakOgVedtak.first
    val regulering = sak.opprettEllerOppdaterRegulering(regulerFraOgMed, clock).getOrFail()

    return Pair(
        sak.nyRegulering(regulering),
        regulering,
    )
}

fun stansetSøknadsbehandlingMedÅpenRegulering(
    regulerFraOgMed: Måned,
    clock: Clock = fixedClock,
): Pair<Sak, OpprettetRegulering> {
    val sakOgVedtak = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        clock = clock,
    )
    val sak = sakOgVedtak.first
    val regulering = sak.opprettEllerOppdaterRegulering(
        fraOgMedMåned = regulerFraOgMed,
        clock = clock,
    ).getOrFail()

    return Pair(
        sak.nyRegulering(regulering),
        regulering,
    )
}

@Suppress("unused")
fun innvilgetSøknadsbehandlingMedIverksattRegulering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    clock: Clock = TikkendeKlokke(),
): Pair<Sak, IverksattRegulering> {
    val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        clock = clock,
    )
    val sak = sakOgVedtak.first
    val regulering = iverksattAutomatiskRegulering()

    return Pair(
        sak.nyRegulering(regulering),
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
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler(SAKSBEHANDLER_NAVN),
    reguleringstype: Reguleringstype = Reguleringstype.MANUELL(emptySet()),
    sakstype: Sakstype = Sakstype.UFØRE,
    avsluttetTidspunkt: Clock = enUkeEtterFixedClock,
    avsluttetAv: NavIdentBruker = saksbehandler,
): AvsluttetRegulering {
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
    ).avslutt(avsluttetAv, avsluttetTidspunkt)
}
