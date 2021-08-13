package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock

/**
 * Ikke journalført eller distribuert brev.
 * Oversendt utbetaling med kvittering.
 */
fun vedtakSøknadsbehandlingIverksattInnvilget(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregning(),
    utbetalingId: UUID30 = UUID30.randomUUID(),
    clock: Clock = fixedClock,
): Pair<Sak, Vedtak.EndringIYtelse> {
    return søknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
    ).let { (sak, søknadsbehandling) ->
        val utbetaling = oversendtUtbetalingMedKvittering(
            saksnummer = saksnummer,
            sakId = sakId,
            fnr = fnr,
            id = utbetalingId,
            eksisterendeUtbetalinger = sak.utbetalinger,
        )
        val vedtak = Vedtak.fromSøknadsbehandling(
            søknadsbehandling = søknadsbehandling,
            utbetalingId = utbetalingId,
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
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    grunnlagsdata: Grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
    vilkårsvurderinger: Vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),
    beregning: Beregning = beregningAvslag(),
): Pair<Sak, Vedtak.Avslag.AvslagBeregning> {
    return søknadsbehandlingIverksattAvslagMedBeregning(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        beregning = beregning,
    ).let { (sak, søknadsbehandling) ->
        val vedtak = Vedtak.Avslag.fromSøknadsbehandlingMedBeregning(
            avslag = søknadsbehandling,
            clock = fixedClock,
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
    revurderingsperiode: Periode = periode2021,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger(
        grunnlagsdata = grunnlagsdataEnsligUtenFradrag(stønadsperiode.periode),
        vilkårsvurderinger = vilkårsvurderingerInnvilget(stønadsperiode.periode),

    ),
    utbetalingId: UUID30 = UUID30.randomUUID(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes>,
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    clock: Clock = fixedClock,
): Pair<Sak, Vedtak.EndringIYtelse> {
    return iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val utbetaling = oversendtUtbetalingMedKvittering(
            saksnummer = saksnummer,
            sakId = sakId,
            fnr = fnr,
            id = utbetalingId,
            eksisterendeUtbetalinger = sak.utbetalinger,
        )
        val vedtak = Vedtak.from(
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

val journalpostIdVedtak = JournalpostId("journalpostIdVedtak")
val brevbestillingIdVedtak = BrevbestillingId("brevbestillingIdVedtak")
val journalført: JournalføringOgBrevdistribusjon.Journalført = JournalføringOgBrevdistribusjon.Journalført(
    journalpostId = journalpostIdVedtak,
)
val journalførtOgDistribuertBrev: JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev =
    JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
        journalpostId = journalpostIdVedtak,
        brevbestillingId = brevbestillingIdVedtak,
    )
