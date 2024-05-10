package no.nav.su.se.bakover.test

import arrow.core.right
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.EksternSupplementRegulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.opprettEllerOppdaterRegulering
import no.nav.su.se.bakover.domain.regulering.supplement.Eksternvedtak
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import no.nav.su.se.bakover.domain.sak.nyRegulering
import no.nav.su.se.bakover.test.utbetaling.simulertUtbetaling
import vilkår.common.domain.Vilkår
import vilkår.common.domain.grunnlag.Grunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import økonomi.domain.simulering.Simuleringsresultat
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
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
    eksternSupplementRegulering: EksternSupplementRegulering = nyEksternSupplementRegulering(),
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
    eksternSupplementRegulering = eksternSupplementRegulering,
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
    supplement: Reguleringssupplement = Reguleringssupplement.empty(clock),
    gVerdiØkning: BigDecimal = BigDecimal(100),
): Pair<Sak, OpprettetRegulering> {
    val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        clock = clock,
    )
    val sak = sakOgVedtak.first
    val regulering = sak.opprettEllerOppdaterRegulering(regulerFraOgMed, clock, supplement, gVerdiØkning).getOrFail()

    return Pair(
        sak.nyRegulering(regulering),
        regulering,
    )
}

fun stansetSøknadsbehandlingMedÅpenRegulering(
    regulerFraOgMed: Måned,
    clock: Clock = fixedClock,
    supplement: Reguleringssupplement = Reguleringssupplement.empty(clock),
    gVerdiØkning: BigDecimal = BigDecimal(100),
): Pair<Sak, OpprettetRegulering> {
    val sakOgVedtak = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        clock = clock,
    )
    val sak = sakOgVedtak.first
    val regulering = sak.opprettEllerOppdaterRegulering(
        fraOgMedMåned = regulerFraOgMed,
        clock = clock,
        supplement = supplement,
        omregningsfaktor = gVerdiØkning,
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

fun nyReguleringssupplement(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    originalCsv: String = "",
    vararg supplementFor: ReguleringssupplementFor = arrayOf(nyReguleringssupplementFor()),
): Reguleringssupplement = Reguleringssupplement(id, opprettet, supplementFor.toList(), originalCsv)

fun nyEksternSupplementRegulering(
    id: UUID? = UUID.randomUUID(),
    bruker: ReguleringssupplementFor? = null,
    eps: List<ReguleringssupplementFor> = emptyList(),
): EksternSupplementRegulering = EksternSupplementRegulering(
    supplementId = id,
    bruker = bruker,
    eps = eps,
)

fun nyReguleringssupplementFor(
    fnr: Fnr = Fnr.generer(),
    vararg innhold: ReguleringssupplementFor.PerType = arrayOf(nyReguleringssupplementInnholdPerType()),
): ReguleringssupplementFor = ReguleringssupplementFor(
    fnr = fnr,
    perType = innhold.toList().toNonEmptyList(),
)

fun nyReguleringssupplementInnholdPerType(
    kategori: Fradragstype.Kategori = Fradragstype.Alderspensjon.kategori,
    vedtak: List<Eksternvedtak> = listOf(
        nyEksternvedtakEndring(),
        nyEksternvedtakRegulering(),
    ),
): ReguleringssupplementFor.PerType = ReguleringssupplementFor.PerType(
    vedtak = vedtak.toNonEmptyList(),
    kategori = kategori,
)

fun nyEksternvedtakRegulering(
    fraOgMed: LocalDate = 1.mai(2021),
    tilOgMed: LocalDate? = null,
    beløp: Int = 1000,
    fradrag: List<ReguleringssupplementFor.PerType.Fradragsperiode> = listOf(nyFradragperiodeRegulering(beløp = beløp, fraOgMed = fraOgMed, tilOgMed = tilOgMed)),
): Eksternvedtak.Regulering {
    return Eksternvedtak.Regulering(
        periode = PeriodeMedOptionalTilOgMed(
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
        ),
        fradrag = fradrag.toNonEmptyList(),
        beløp = beløp,
    )
}

fun nyEksternvedtakEndring(
    periode: PeriodeMedOptionalTilOgMed = PeriodeMedOptionalTilOgMed(
        fraOgMed = 1.april(2021),
        tilOgMed = 30.april(2021),
    ),
    beløp: Int = 1000,
    fradrag: List<ReguleringssupplementFor.PerType.Fradragsperiode> = listOf(nyFradragperiodeEndring(beløp = beløp, fraOgMed = periode.fraOgMed, tilOgMed = periode.tilOgMed)),
): Eksternvedtak.Endring {
    return Eksternvedtak.Endring(
        periode = periode,
        fradrag = fradrag.toNonEmptyList(),
        beløp = beløp,
    )
}

fun nyFradragperiodeEndring(
    fraOgMed: LocalDate = 1.april(2021),
    tilOgMed: LocalDate? = 30.april(2021),
    vedtakstype: ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring,
    beløp: Int = 1000,
    eksterndata: ReguleringssupplementFor.PerType.Fradragsperiode.Eksterndata = nyEksterndata(),
): ReguleringssupplementFor.PerType.Fradragsperiode = ReguleringssupplementFor.PerType.Fradragsperiode(
    fraOgMed = fraOgMed,
    tilOgMed = tilOgMed,
    vedtakstype = vedtakstype,
    beløp = beløp,
    eksterndata = eksterndata,
)

fun nyFradragperiodeRegulering(
    fraOgMed: LocalDate = 1.mai(2021),
    tilOgMed: LocalDate? = null,
    vedtakstype: ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype = ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering,
    beløp: Int = 1000,
    eksterndata: ReguleringssupplementFor.PerType.Fradragsperiode.Eksterndata = nyEksterndata(),
): ReguleringssupplementFor.PerType.Fradragsperiode = ReguleringssupplementFor.PerType.Fradragsperiode(
    fraOgMed = fraOgMed,
    tilOgMed = tilOgMed,
    vedtakstype = vedtakstype,
    beløp = beløp,
    eksterndata = eksterndata,
)

fun nyEksterndata(
    fnr: String = "11111111111",
    sakstype: String = "UFOREP",
    vedtakstype: String = "REGULERING",
    fraOgMed: String = "01.05.2021",
    tilOgMed: String? = null,
    bruttoYtelse: String = "10000",
    nettoYtelse: String = "11000",
    ytelseskomponenttype: String = "ST",
    bruttoYtelseskomponent: String = "10000",
    nettoYtelseskomponent: String = "11000",
): ReguleringssupplementFor.PerType.Fradragsperiode.Eksterndata {
    return ReguleringssupplementFor.PerType.Fradragsperiode.Eksterndata(
        fnr = fnr,
        sakstype = sakstype,
        vedtakstype = vedtakstype,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        bruttoYtelse = bruttoYtelse,
        nettoYtelse = nettoYtelse,
        ytelseskomponenttype = ytelseskomponenttype,
        bruttoYtelseskomponent = bruttoYtelseskomponent,
        nettoYtelseskomponent = nettoYtelseskomponent,
    )
}
