package no.nav.su.se.bakover.test

import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import beregning.domain.BeregningStrategyFactory
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.domain.tid.periode.Perioder
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.EksterntBeløpSomFradragstype
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.RegulertBeløp
import no.nav.su.se.bakover.domain.regulering.hentGjeldendeVedtaksdataForRegulering
import no.nav.su.se.bakover.domain.regulering.opprettReguleringForAutomatiskEllerManuellBehandling
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.sak.nyRegulering
import no.nav.su.se.bakover.test.utbetaling.simulertUtbetaling
import satser.domain.SatsFactory
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.common.domain.Vilkår
import vilkår.common.domain.grunnlag.Grunnlag
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

fun opprettetRegulering(
    id: ReguleringId = ReguleringId.generer(),
    sakId: UUID = UUID.randomUUID(),
    reguleringsperiode: Periode = stønadsperiode2021.periode,
    saksnummer: Saksnummer = Saksnummer(2021),
    opprettet: Tidspunkt = fixedTidspunkt,
    fnr: Fnr = Fnr.generer(),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering = GrunnlagsdataOgVilkårsvurderingerRevurdering(
        grunnlagsdataEnsligUtenFradrag(periode = reguleringsperiode),
        vilkårsvurderingerRevurderingInnvilget(periode = reguleringsperiode),
    ),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler(SAKSBEHANDLER_NAVN),
    reguleringstype: Reguleringstype = Reguleringstype.MANUELL(emptySet()),
    sakstype: Sakstype = Sakstype.UFØRE,
    eksterntRegulerteBeløp: EksterntRegulerteBeløp = tomEksterntRegulerteBeløp(fnr),

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
    eksterntRegulerteBeløp = eksterntRegulerteBeløp,
)

fun iverksattAutomatiskRegulering(
    id: ReguleringId = ReguleringId.generer(),
    sakId: UUID = UUID.randomUUID(),
    reguleringsperiode: Periode = stønadsperiode2021.periode,
    saksnummer: Saksnummer = Saksnummer(2021),
    opprettet: Tidspunkt = fixedTidspunkt,
    fnr: Fnr = Fnr.generer(),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering = GrunnlagsdataOgVilkårsvurderingerRevurdering(
        grunnlagsdataEnsligUtenFradrag(reguleringsperiode),
        vilkårsvurderingerRevurderingInnvilget(reguleringsperiode),
    ),
    reguleringstype: Reguleringstype = Reguleringstype.AUTOMATISK,
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
    clock: Clock = fixedClock,
    sakstype: Sakstype = Sakstype.UFØRE,
): IverksattRegulering {
    val opprettet = opprettetRegulering(
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
    val beregning = opprettet.beregn(satsFactoryTestPåDato(), null, clock)
    val simulering = simulertUtbetaling().simulering
    val beregnetRegulering = opprettet.tilBeregnet(beregning, simulering)
    return beregnetRegulering.tilAttestering(saksbehandler)
        .godkjenn(NavIdentBruker.Attestant(saksbehandler.navIdent), clock)
}

fun ReguleringUnderBehandling.beregn(
    satsFactory: SatsFactory,
    begrunnelse: String? = null,
    clock: Clock,
) = BeregningStrategyFactory(
    clock = clock,
    satsFactory = satsFactory,
).beregn(
    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
    begrunnelse = begrunnelse,
    sakstype = sakstype,
)

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
    val sakerMedEksterntRegulerteBeløp = eksterneReguleringer(sak)
    val vedtakSomKanRevurderes = sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
    val vedtaksdata = hentGjeldendeVedtaksdataForRegulering(regulerFraOgMed, sak.info(), vedtakSomKanRevurderes, clock).getOrFail()
    val regulering = sak.opprettReguleringForAutomatiskEllerManuellBehandling(
        clock,
        vedtaksdata,
        sakerMedEksterntRegulerteBeløp,
        satsFactoryTestPåDato(),
    ).getOrFail()

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
    val vedtakSomKanRevurderes = sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
    val vedtaksdata = hentGjeldendeVedtaksdataForRegulering(regulerFraOgMed, sak.info(), vedtakSomKanRevurderes, clock).getOrFail()
    val regulering = OpprettetRegulering(
        id = ReguleringId.generer(),
        opprettet = Tidspunkt.now(clock),
        sakId = sak.id,
        saksnummer = sak.saksnummer,
        saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
        fnr = sak.fnr,
        grunnlagsdataOgVilkårsvurderinger = vedtaksdata.grunnlagsdataOgVilkårsvurderinger,
        beregning = null,
        simulering = null,
        reguleringstype = Reguleringstype.MANUELL(ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset("Stanset")),
        sakstype = sak.type,
        eksterntRegulerteBeløp = tomEksterntRegulerteBeløp(sak.fnr),
    )

    return Pair(
        sak.nyRegulering(regulering),
        regulering,
    )
}

fun tomEksterntRegulerteBeløp(fnr: Fnr): EksterntRegulerteBeløp = EksterntRegulerteBeløp(
    brukerFnr = fnr,
    beløpBruker = emptyList(),
    beløpEps = emptyList(),
    inntektEtterUføre = null,
)

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
    id: ReguleringId = ReguleringId.generer(),
    sakId: UUID = UUID.randomUUID(),
    reguleringsperiode: Periode = stønadsperiode2021.periode,
    saksnummer: Saksnummer = Saksnummer(2021),
    opprettet: Tidspunkt = fixedTidspunkt,
    fnr: Fnr = Fnr.generer(),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering = GrunnlagsdataOgVilkårsvurderingerRevurdering(
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

fun nyÅrsakDifferanseEtterRegulering(
    forventetBeløpEtterRegulering: BigDecimal = BigDecimal(1000),
    eksternNettoBeløpEtterRegulering: BigDecimal = BigDecimal(1100),
    eksternBruttoBeløpEtterRegulering: BigDecimal = BigDecimal(1100),
    vårtBeløpFørRegulering: BigDecimal = BigDecimal(1000),
    fradragskategori: Fradragstype.Kategori = Fradragstype.Uføretrygd.kategori,
    fradragTilhører: FradragTilhører = FradragTilhører.BRUKER,
    begrunnelse: String = "Begrunnelse",
): ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering =
    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering(
        forventetBeløpEtterRegulering = forventetBeløpEtterRegulering,
        eksternBruttoBeløpEtterRegulering = eksternBruttoBeløpEtterRegulering,
        eksternNettoBeløpEtterRegulering = eksternNettoBeløpEtterRegulering,
        vårtBeløpFørRegulering = vårtBeløpFørRegulering,
        fradragskategori = fradragskategori,
        fradragTilhører = fradragTilhører,
        begrunnelse = begrunnelse,
    )

fun nyÅrsakDifferanseFørRegulering(
    vårtBeløpFørRegulering: BigDecimal = BigDecimal(1000),
    eksternNettoBeløpFørRegulering: BigDecimal = BigDecimal(1100),
    eksternBruttoBeløpFørRegulering: BigDecimal = BigDecimal(1100),
    fradragskategori: Fradragstype.Kategori = Fradragstype.Uføretrygd.kategori,
    fradragTilhører: FradragTilhører = FradragTilhører.BRUKER,
    begrunnelse: String = "Begrunnelse",
): ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering =
    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering(
        vårtBeløpFørRegulering = vårtBeløpFørRegulering,
        eksternBruttoBeløpFørRegulering = eksternBruttoBeløpFørRegulering,
        eksternNettoBeløpFørRegulering = eksternNettoBeløpFørRegulering,
        fradragskategori = fradragskategori,
        fradragTilhører = fradragTilhører,
        begrunnelse = begrunnelse,
    )

fun nyÅrsakFantIkkeVedtakForApril(
    fradragskategori: Fradragstype.Kategori = Fradragstype.Uføretrygd.kategori,
    fradragTilhører: FradragTilhører = FradragTilhører.BRUKER,
    begrunnelse: String = "Begrunnelse",
): ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FantIkkeVedtakForApril =
    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FantIkkeVedtakForApril(
        fradragskategori = fradragskategori,
        fradragTilhører = fradragTilhører,
        begrunnelse = begrunnelse,
    )

fun nyÅrsakBrukerManglerSupplement(
    fradragskategori: Fradragstype.Kategori = Fradragstype.Uføretrygd.kategori,
    fradragTilhører: FradragTilhører = FradragTilhører.BRUKER,
    begrunnelse: String = "Begrunnelse",
): ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement =
    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement(
        fradragskategori = fradragskategori,
        fradragTilhører = fradragTilhører,
        begrunnelse = begrunnelse,
    )

fun nyÅrsakFinnesFlerePerioderAvFradrag(
    fradragskategori: Fradragstype.Kategori = Fradragstype.Uføretrygd.kategori,
    fradragTilhører: FradragTilhører = FradragTilhører.BRUKER,
    begrunnelse: String = "Begrunnelse",
): ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag =
    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag(
        fradragskategori = fradragskategori,
        fradragTilhører = fradragTilhører,
        begrunnelse = begrunnelse,
    )

fun nyÅrsakFradragErUtenlandsinntekt(
    fradragskategori: Fradragstype.Kategori = Fradragstype.Uføretrygd.kategori,
    fradragTilhører: FradragTilhører = FradragTilhører.BRUKER,
    begrunnelse: String = "Begrunnelse",
): ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt =
    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt(
        fradragskategori = fradragskategori,
        fradragTilhører = fradragTilhører,
        begrunnelse = begrunnelse,
    )

fun nyÅrsakSupplementHarFlereVedtaksperioderForFradrag(
    fradragskategori: Fradragstype.Kategori = Fradragstype.Uføretrygd.kategori,
    fradragTilhører: FradragTilhører = FradragTilhører.BRUKER,
    begrunnelse: String = "Begrunnelse",
    eksterneReguleringsvedtakperioder: List<PeriodeMedOptionalTilOgMed> = listOf(
        PeriodeMedOptionalTilOgMed(
            fraOgMed = 1.mai(2021),
            tilOgMed = null,
        ),
    ),
): ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag =
    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag(
        fradragskategori = fradragskategori,
        fradragTilhører = fradragTilhører,
        begrunnelse = begrunnelse,
        eksterneReguleringsvedtakperioder = eksterneReguleringsvedtakperioder,
    )

fun nyÅrsakSupplementInneholderIkkeFradraget(
    fradragskategori: Fradragstype.Kategori = Fradragstype.Uføretrygd.kategori,
    fradragTilhører: FradragTilhører = FradragTilhører.BRUKER,
    begrunnelse: String = "Begrunnelse",
): ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget =
    ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget(
        fradragskategori = fradragskategori,
        fradragTilhører = fradragTilhører,
        begrunnelse = begrunnelse,
    )

fun nyÅrsakDelvisOpphør(
    opphørsperioder: Perioder = Perioder.create(listOf(år(2021))),
    begrunnelse: String = "Begrunnelse",
): ÅrsakTilManuellRegulering.DelvisOpphør =
    ÅrsakTilManuellRegulering.DelvisOpphør(opphørsperioder = opphørsperioder, begrunnelse = begrunnelse)

fun nyÅrsakVedtakstidslinjeErIkkeSammenhengende(
    begrunnelse: String = "Begrunnelse",
): ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende =
    ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende(begrunnelse = begrunnelse)

fun nyÅrsakYtelseErMidlertidigStanset(
    begrunnelse: String = "Begrunnelse",
): ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset =
    ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset(begrunnelse = begrunnelse)

fun eksterneReguleringer(
    sak: Sak,
    fradragstype: Fradragstype = Fradragstype.Alderspensjon,
    førRegulering: Int = 100,
    etterRegulering: Int = 110,
) = listOf(
    EksterntRegulerteBeløp(
        brukerFnr = sak.fnr,
        beløpBruker = listOf(
            RegulertBeløp(
                fnr = sak.fnr,
                fradragstype = EksterntBeløpSomFradragstype.from(fradragstype),
                førRegulering = BigDecimal.valueOf(førRegulering.toLong()).setScale(2),
                etterRegulering = BigDecimal.valueOf(etterRegulering.toLong()).setScale(2),
            ),
        ),
        beløpEps = emptyList(),
    ),
)
