package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
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
    clock: Clock = fixedClock,
    avkorting: AvkortingVedSøknadsbehandling.Uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
): Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling> {
    require(
        grunnlagsdata.bosituasjon.all { it is Grunnlag.Bosituasjon.Fullstendig },
    )
    return søknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        avkorting = avkorting,
    ).let { (sak, søknadsbehandling) ->
        val utbetaling = nyUtbetalingOversendtMedKvittering(
            sakOgBehandling = sak to søknadsbehandling,
            beregning = søknadsbehandling.beregning,
            clock = clock,
        )
        val vedtak = VedtakSomKanRevurderes.fromSøknadsbehandling(
            søknadsbehandling = søknadsbehandling,
            utbetalingId = utbetaling.id,
            clock = clock,
        )
        Pair(
            sak.copy(
                vedtakListe = nonEmptyListOf(vedtak),
                utbetalinger = nonEmptyListOf(utbetaling),
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
): Pair<Sak, Avslagsvedtak.AvslagBeregning> {
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
): Pair<Sak, Avslagsvedtak.AvslagVilkår> {
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
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = stønadsperiode,
    ),
    clock: Clock = fixedClock,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = innvilgetGrunnlagsdataOgVilkårsvurderinger(
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsperiode = revurderingsperiode,
        clock = clock,
    ),
    utbetalingId: UUID30 = UUID30.randomUUID(),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering> {
    return iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val utbetaling = oversendtUtbetalingMedKvittering(
            id = utbetalingId,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
        )
        val vedtak = VedtakSomKanRevurderes.from(
            revurdering = revurdering,
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

fun vedtakIverksattAutomatiskRegulering(
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    regulerFraOgMed: Periode = stønadsperiode.periode,
    clock: Clock = fixedClock,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
        grunnlagsdataEnsligUtenFradrag(periode = regulerFraOgMed),
        vilkårsvurderingerRevurderingInnvilget(periode = regulerFraOgMed),
    ),
    utbetalingId: UUID30 = UUID30.randomUUID(),
): Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse> {
    assert(stønadsperiode.inneholder(regulerFraOgMed))

    return vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = stønadsperiode,
        grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
        vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.tilVilkårsvurderingerSøknadsbehandling(),
        clock = clock,
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

fun vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    clock: Clock = TikkendeKlokke(fixedClock),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = år(2021).tilOgMed,
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode),
        clock = clock,
    ),
    attestering: Attestering = attesteringIverksatt(clock = clock),
): Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse> {
    return iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        attestering = attestering,
        clock = clock,
    ).let { (sak, revurdering) ->
        val utbetaling = oversendtStansUtbetalingUtenKvittering(
            stansDato = revurdering.periode.fraOgMed,
            fnr = sak.fnr,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
        )

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

fun vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
    clock: Clock = TikkendeKlokke(fixedClock),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = år(2021).tilOgMed,
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        clock = clock,
    ),
    attestering: Attestering = attesteringIverksatt(clock = clock),
): Pair<Sak, VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse> {
    return iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse(
        periode = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        attestering = attestering,
        clock = clock,
    ).let { (sak, revurdering) ->
        val utbetaling = oversendtGjenopptakUtbetalingUtenKvittering(
            fnr = sak.fnr,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
        )

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
