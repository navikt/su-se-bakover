package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Ikke journalført eller distribuert brev.
 * Oversendt utbetaling med kvittering.
 * @param grunnlagsdata bosituasjon må være fullstendig
 */
fun vedtakSøknadsbehandlingIverksattInnvilget(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = vilkårsvurderingerSøknadsbehandlingInnvilget(
        periode = stønadsperiode.periode,
        bosituasjon = grunnlagsdata.bosituasjon.map { it as Grunnlag.Bosituasjon.Fullstendig }.toNonEmptyList(),
    ),
    clock: Clock = tikkendeFixedClock(),
): Pair<Sak, VedtakInnvilgetSøknadsbehandling> {
    require(
        grunnlagsdata.bosituasjon.all { it is Grunnlag.Bosituasjon.Fullstendig },
    )
    return søknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        clock = clock,
    ).let { (sak, søknadsbehandling) ->
        // TODO jah: Bruk vedtaket + utbetalingen som kommer fra søknadsbehandlingIverksattInnvilget-kallet
        val utbetaling = simulerUtbetaling(
            sak = sak,
            søknadsbehandling = søknadsbehandling,
            simuleringsperiode = søknadsbehandling.periode,
            behandler = søknadsbehandling.attesteringer.hentSisteAttestering().attestant,
            clock = clock,
        ).getOrFail().let {
            it.toOversendtUtbetaling(UtbetalingStub.generateRequest(it))
                .toKvittertUtbetaling(kvittering())
        }

        val vedtak = VedtakSomKanRevurderes.from(
            søknadsbehandling = søknadsbehandling,
            utbetalingId = utbetaling.id,
            clock = clock,
        )
        Pair(
            sak.copy(
                vedtakListe = nonEmptyListOf(vedtak),
                utbetalinger = Utbetalinger(nonEmptyListOf(utbetaling)),
            ),
            vedtak,
        )
    }
}

/**
 * Ikke journalført eller distribuert brev
 */
fun vedtakSøknadsbehandlingIverksattAvslagMedBeregning(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerSøknadsbehandlingInnvilget(
        periode = stønadsperiode.periode,
        uføre = innvilgetUførevilkårForventetInntekt0(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
            uføregrunnlag = uføregrunnlagForventetInntekt(
                periode = stønadsperiode.periode,
                forventetInntekt = 1_000_000,
            ),
        ),
    ),
    clock: Clock = fixedClock,
): Pair<Sak, VedtakAvslagBeregning> {
    return søknadsbehandlingIverksattAvslagMedBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val vedtak = Avslagsvedtak.fromSøknadsbehandlingMedBeregning(
            avslag = søknadsbehandling,
            clock = clock,
        )
        Pair(
            sak.copy(
                vedtakListe = sak.vedtakListe + vedtak,
            ),
            vedtak,
        )
    }
}

/**
 * Ikke journalført eller distribuert brev
 */
fun vedtakSøknadsbehandlingIverksattAvslagUtenBeregning(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling.Uføre = vilkårsvurderingerAvslåttAlle(stønadsperiode.periode),
    clock: Clock = fixedClock,
): Pair<Sak, VedtakAvslagVilkår> {
    return søknadsbehandlingIverksattAvslagUtenBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
    ).let { (sak, søknadsbehandling) ->
        val vedtak = Avslagsvedtak.fromSøknadsbehandlingUtenBeregning(
            avslag = søknadsbehandling,
            clock = clock,
        )
        Pair(
            sak.copy(
                vedtakListe = sak.vedtakListe + vedtak,
            ),
            vedtak,
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
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    brevvalg: BrevvalgRevurdering = sendBrev(),
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
    clock: Clock = fixedClock,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
        grunnlagsdataEnsligUtenFradrag(periode = regulerFraOgMed),
        vilkårsvurderingerRevurderingInnvilget(periode = regulerFraOgMed),
    ),
    utbetalingId: UUID30 = UUID30.randomUUID(),
): Pair<Sak, VedtakEndringIYtelse> {
    assert(stønadsperiode.inneholder(regulerFraOgMed))

    return vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
        vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.tilVilkårsvurderingerSøknadsbehandling(),
        clock = clock,
    ).let { (sak, _) ->
        val regulering = iverksattAutomatiskRegulering(sakId = sak.id, reguleringsperiode = regulerFraOgMed)

        @Suppress("UNCHECKED_CAST")
        val utbetaling = oversendtUtbetalingMedKvittering(
            id = utbetalingId,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
            eksisterendeUtbetalinger = sak.utbetalinger as List<Utbetaling.OversendtUtbetaling>,
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

fun vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    clock: Clock = TikkendeKlokke(1.januar(2021).fixedClock()),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode),
        clock = clock,
    ),
    attestering: Attestering = attesteringIverksatt(clock = clock),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Triple<Sak, VedtakStansAvYtelse, Utbetaling.OversendtUtbetaling.UtenKvittering> {
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
            it.toOversendtUtbetaling(UtbetalingStub.generateRequest(it))
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
            it.toOversendtUtbetaling(UtbetalingStub.generateRequest(it))
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
