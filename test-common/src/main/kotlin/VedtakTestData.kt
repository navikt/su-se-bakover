package no.nav.su.se.bakover.test

import dokument.domain.JournalføringOgBrevdistribusjon
import dokument.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Attestering
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.startOfMonth
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.test.eksterneGrunnlag.eksternGrunnlagHentet
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.simulering.simulerGjenopptak
import no.nav.su.se.bakover.test.simulering.simulerStans
import no.nav.su.se.bakover.test.utbetaling.kvittering
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import økonomi.domain.kvittering.Kvittering
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Ikke journalført eller distribuert brev.
 * Oversendt utbetaling uten kvittering.
 * @param grunnlagsdata bosituasjon må være fullstendig
 */
fun vedtakSøknadsbehandlingIverksattInnvilget(
    clock: Clock = TikkendeKlokke(),
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = UUID.randomUUID(),
    fnr: Fnr = Fnr.generer(),
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        sakInfo = SakInfo(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            type = Sakstype.UFØRE,
        ),
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = emptyList(),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    attestering: Attestering.Iverksatt = attesteringIverksatt(clock),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    kvittering: Kvittering? = kvittering(clock = clock),
): Pair<Sak, VedtakInnvilgetSøknadsbehandling> {
    return iverksattSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        attestering = attestering,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
        kvittering = kvittering,
    ).let {
        Pair(
            it.first,
            it.third as VedtakInnvilgetSøknadsbehandling,
        )
    }
}

/**
 * Ikke journalført eller distribuert brev
 */
fun vedtakSøknadsbehandlingIverksattAvslagMedBeregning(
    clock: Clock = TikkendeKlokke(),
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = listOf(
        innvilgetUførevilkårForventetInntekt0(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
            uføregrunnlag = uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            ),
        ),
    ),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    attestering: Attestering.Iverksatt = attesteringIverksatt(clock),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    kvittering: Kvittering? = kvittering(clock = clock),
): Pair<Sak, VedtakAvslagBeregning> {
    return iverksattSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        attestering = attestering,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
        kvittering = kvittering,
    ).let {
        Pair(
            it.first,
            it.third as VedtakAvslagBeregning,
        )
    }
}

/**
 * Ikke journalført eller distribuert brev
 */
fun vedtakSøknadsbehandlingIverksattAvslagUtenBeregning(
    clock: Clock = TikkendeKlokke(),
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    sakOgSøknad: Pair<Sak, Søknad.Journalført.MedOppgave> = nySakUføre(
        clock = clock,
    ),
    customGrunnlag: List<Grunnlag> = emptyList(),
    customVilkår: List<Vilkår> = listOf(institusjonsoppholdvilkårAvslag()),
    eksterneGrunnlag: EksterneGrunnlag = eksternGrunnlagHentet(),
    attestering: Attestering.Iverksatt = attesteringIverksatt(clock),
    fritekstTilBrev: String = "",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    kvittering: Kvittering? = kvittering(clock = clock),
): Pair<Sak, VedtakAvslagVilkår> {
    return iverksattSøknadsbehandling(
        clock = clock,
        stønadsperiode = stønadsperiode,
        sakOgSøknad = sakOgSøknad,
        customGrunnlag = customGrunnlag,
        customVilkår = customVilkår,
        eksterneGrunnlag = eksterneGrunnlag,
        attestering = attestering,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
        kvittering = kvittering,
    ).let {
        Pair(
            it.first,
            it.third as VedtakAvslagVilkår,
        )
    }
}

/**
 * Ikke journalført eller distribuert brev
 */
fun vedtakRevurderingIverksattInnvilget(
    clock: Clock = tikkendeFixedClock(),
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    attestant: NavIdentBruker.Attestant = no.nav.su.se.bakover.test.attestant,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
    brevvalg: BrevvalgRevurdering.Valgt = sendBrev(),
    skalTilbakekreve: Boolean = true,
): Pair<Sak, VedtakInnvilgetRevurdering> {
    return iverksattRevurdering(
        clock = clock,
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
        attestant = attestant,
        saksbehandler = saksbehandler,
        attesteringsoppgaveId = attesteringsoppgaveId,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
        brevvalg = brevvalg,
        skalTilbakekreve = skalTilbakekreve,
    ).let { (sak, _, _, vedtak) ->
        sak to vedtak.shouldBeType()
    }
}

fun vedtakIverksattAutomatiskRegulering(
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    regulerFraOgMed: Periode = stønadsperiode.periode,
    clock: Clock = TikkendeKlokke(),
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    utbetalingId: UUID30 = UUID30.randomUUID(),
): Pair<Sak, VedtakEndringIYtelse> {
    require(stønadsperiode.inneholder(regulerFraOgMed))

    return vedtakSøknadsbehandlingIverksattInnvilget(
        clock = clock,
        stønadsperiode = stønadsperiode,
        customGrunnlag = grunnlagsdataOverrides,
        customVilkår = vilkårOverrides,
    ).let { (sak, _) ->
        val regulering = iverksattAutomatiskRegulering(sakId = sak.id, reguleringsperiode = regulerFraOgMed)

        val utbetaling = oversendtUtbetalingMedKvittering(
            id = utbetalingId,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
        )
        val vedtak = VedtakSomKanRevurderes.from(
            regulering = regulering,
            utbetalingId = utbetalingId,
            clock = clock,
        )

        Pair(
            sak.copy(
                vedtakListe = sak.vedtakListe + vedtak,
                utbetalinger = sak.utbetalinger + utbetaling,
            ),
            vedtak,
        )
    }
}

/**
 * @param kvittering Defaulter til OK. Dersom null, så blir ikke utbetalingen kvittert.
 */
fun vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    clock: Clock = TikkendeKlokke(),
    periode: Periode = år(2021),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode),
        clock = clock,
    ),
    attestering: Attestering = attesteringIverksatt(clock = clock),
    utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
    kvittering: Kvittering? = kvittering(clock = clock),
): Triple<Sak, VedtakStansAvYtelse, Utbetaling.OversendtUtbetaling> {
    return iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        attestering = attestering,
        clock = clock,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { (sak, revurdering) ->
        val utbetaling = simulerStans(
            sak = sak,
            stans = revurdering,
            stansDato = revurdering.periode.fraOgMed,
            behandler = revurdering.attesteringer.hentSisteAttestering().attestant,
            clock = clock,
            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
        ).getOrFail().let {
            it.toOversendtUtbetaling(UtbetalingStub.generateRequest(it)).let {
                if (kvittering != null) {
                    it.toKvittertUtbetaling(kvittering)
                } else {
                    it
                }
            }
        }

        val vedtak = VedtakSomKanRevurderes.from(
            revurdering = revurdering,
            utbetalingId = utbetaling.id,
            clock = clock,
        )

        Triple(
            sak.copy(
                vedtakListe = sak.vedtakListe + vedtak,
                utbetalinger = sak.utbetalinger + utbetaling,
            ),
            vedtak,
            utbetaling,
        )
    }
}

fun vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
    clock: Clock = TikkendeKlokke(fixedClock),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = år(2021).tilOgMed,
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        clock = clock,
    ).let { it.first to it.second },
    attestering: Attestering = attesteringIverksatt(clock = clock),
    kvittering: Kvittering? = kvittering(clock = clock),
): Pair<Sak, VedtakGjenopptakAvYtelse> {
    return iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse(
        periode = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        attestering = attestering,
        clock = clock,
    ).let { (sak, revurdering) ->
        val utbetaling = simulerGjenopptak(
            sak = sak,
            gjenopptak = revurdering,
            behandler = revurdering.attesteringer.hentSisteAttestering().attestant,
            clock = clock,
        ).getOrFail().let {
            it.toOversendtUtbetaling(UtbetalingStub.generateRequest(it)).let {
                if (kvittering != null) {
                    it.toKvittertUtbetaling(kvittering)
                } else {
                    it
                }
            }
        }

        val vedtak = VedtakSomKanRevurderes.from(
            revurdering = revurdering,
            utbetalingId = utbetaling.id,
            clock = clock,
        )

        sak.copy(
            vedtakListe = sak.vedtakListe + vedtak,
            utbetalinger = sak.utbetalinger + utbetaling,
        ) to vedtak
    }
}

val journalpostIdVedtak = JournalpostId("journalpostIdVedtak")
val brevbestillingIdVedtak = BrevbestillingId("brevbestillingIdVedtak")
val journalført: JournalføringOgBrevdistribusjon.Journalført = JournalføringOgBrevdistribusjon.Journalført(
    journalpostId = journalpostIdVedtak,
)

@Suppress("unused")
val journalførtOgDistribuertBrev: JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev =
    JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
        journalpostId = journalpostIdVedtak,
        brevbestillingId = brevbestillingIdVedtak,
    )
