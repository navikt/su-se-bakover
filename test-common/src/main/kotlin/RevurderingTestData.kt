package no.nav.su.se.bakover.test

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakFelles
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.LocalDate
import java.util.UUID

val revurderingId: UUID = UUID.randomUUID()

val oppgaveIdRevurdering = OppgaveId("oppgaveIdRevurdering")

/** MELDING_FRA_BRUKER */
val revurderingsårsak =
    Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("revurderingsårsakBegrunnelse"),
    )

/**
 * En revurdering i sin tidligste tilstand der den er basert på et innvilget søknadsbehandlingsvedtak
 *
 * Defaults:
 * - jan til des 2021
 * - Uten fradrag
 * - Enslig ektefelle
 * - Årsak: Melding fra bruker
 */
fun opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        revurderingsperiode,
        fixedClock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): Pair<Sak, OpprettetRevurdering> {
    // Får kompileringsfeil i denne versjonen av Kotlin/Java hvis vi tar vekk type arguments: '<VedtakFelles>'.
    @Suppress("RemoveExplicitTypeArguments")
    require(sakOgVedtakSomKanRevurderes.first.vedtakListe.contains<VedtakFelles>(sakOgVedtakSomKanRevurderes.second)) {
        "Dersom man sender inn vedtak som skal revurderes, må man også sende inn en sak som inneholder nevnt vedtak."
    }
    require(
        stønadsperiode.periode.inneholder(revurderingsperiode) &&
            stønadsperiode.periode.inneholder(
                sakOgVedtakSomKanRevurderes.second.periode,
            ),
    ) {
        "Stønadsperioden (${stønadsperiode.periode}) må inneholde revurderingsperioden ($revurderingsperiode) og tilRevurdering's periode (${sakOgVedtakSomKanRevurderes.second.periode})}"
    }
    val opprettetRevurdering = OpprettetRevurdering(
        id = revurderingId,
        periode = revurderingsperiode,
        opprettet = fixedTidspunkt,
        tilRevurdering = sakOgVedtakSomKanRevurderes.second,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveIdRevurdering,
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
        grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
        vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.tilVilkårsvurderingerRevurdering(),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
    )
    return Pair(
        sakOgVedtakSomKanRevurderes.first.copy(
            // For å støtte revurderinger av revurderinger (burde nok legge inn litt validering)
            revurderinger = sakOgVedtakSomKanRevurderes.first.revurderinger + opprettetRevurdering,
        ),
        opprettetRevurdering,
    )
}

/**
 * En innvilget beregnet revurdering med utgangspunkt i et vedtak fra en innvilget søknadsbehandling.
 */
fun beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        revurderingsperiode,
        fixedClock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): Pair<Sak, BeregnetRevurdering.Innvilget> {

    return opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val innvilgetBeregnetRevurdering =
            revurdering.beregn(sak.utbetalinger).orNull() as BeregnetRevurdering.Innvilget
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == innvilgetBeregnetRevurdering.id } + innvilgetBeregnetRevurdering,
            ),
            innvilgetBeregnetRevurdering,
        )
    }
}

fun lagFradragsgrunnlag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    type: Fradragstype,
    månedsbeløp: Double,
    periode: Periode,
    utenlandskInntekt: UtenlandskInntekt? = null,
    tilhører: FradragTilhører,
) = Grunnlag.Fradragsgrunnlag.tryCreate(
    id = id,
    opprettet = opprettet,
    fradrag = FradragFactory.ny(
        type = type,
        månedsbeløp = månedsbeløp,
        periode = periode,
        utenlandskInntekt = utenlandskInntekt,
        tilhører = tilhører,
    ),
).orNull()!!

fun beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        revurderingsperiode,
        fixedClock,
    ).let {
        it.copy(
            grunnlagsdata = it.grunnlagsdata.copy(
                fradragsgrunnlag = listOf(
                    lagFradragsgrunnlag(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 6000.0,
                        periode = stønadsperiode.periode,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )
    },
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(
        oversendtUtbetalingMedKvittering(
            eksisterendeUtbetalinger = emptyList(),
        ),
    ),
): Pair<Sak, BeregnetRevurdering.IngenEndring> {
    return opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val innvilgetBeregnetRevurdering = revurdering.beregn(eksisterendeUtbetalinger)
            .orNull() as BeregnetRevurdering.IngenEndring
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == innvilgetBeregnetRevurdering.id } + innvilgetBeregnetRevurdering,
                utbetalinger = eksisterendeUtbetalinger,
            ),
            innvilgetBeregnetRevurdering,
        )
    }
}

/**
 * En beregnet revurdering som gir opphør med utgangspunkt i et vedtak fra en innvilget søknadsbehandling.
 *
 * Opphører både på formue+uføre-vilkår
 */
fun beregnetRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        revurderingsperiode,
        fixedClock,
    ).let {
        it.copy(
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgInnvilgetFormue(
                periode = revurderingsperiode,
                bosituasjon = it.grunnlagsdata.bosituasjon.singleFullstendigOrThrow(),
            ),
        )
    },
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): Pair<Sak, BeregnetRevurdering.Opphørt> {

    return opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val opphørtBeregnetRevurdering = revurdering.beregn(sak.utbetalinger)
            .getOrHandle { throw IllegalStateException("Kunne ikke instansiere testdata. Underliggende feil: $it") } as BeregnetRevurdering.Opphørt
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == opphørtBeregnetRevurdering.id } + opphørtBeregnetRevurdering,
            ),
            opphørtBeregnetRevurdering,
        )
    }
}

/**
 * En innvilget simulert revurdering med utgangspunkt i et vedtak fra en innvilget søknadsbehandling.
 */
fun simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        revurderingsperiode,
        fixedClock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): Pair<Sak, SimulertRevurdering.Innvilget> {

    return beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val innvilgetSimulertRevurdering = revurdering.toSimulert(
            simuleringNy(
                eksisterendeUtbetalinger = sak.utbetalinger,
                beregning = revurdering.beregning,
                fnr = revurdering.fnr,
                sakId = revurdering.sakId,
                saksnummer = revurdering.saksnummer,
            ),
        )
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == innvilgetSimulertRevurdering.id } + innvilgetSimulertRevurdering,
            ),
            innvilgetSimulertRevurdering,
        )
    }
}

fun simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        revurderingsperiode,
        fixedClock,
    ).let {
        it.copy(
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgInnvilgetFormue(
                periode = revurderingsperiode,
                bosituasjon = it.grunnlagsdata.bosituasjon.singleFullstendigOrThrow(),
            ),
        )
    },
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): Pair<Sak, SimulertRevurdering.Opphørt> {

    return beregnetRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val opphørtSimulertRevurdering = revurdering.toSimulert(
            simuleringOpphørt(
                opphørsdato = revurdering.periode.fraOgMed,
                eksisterendeUtbetalinger = sak.utbetalinger,
                fnr = revurdering.fnr,
                sakId = revurdering.sakId,
                saksnummer = revurdering.saksnummer,
            ),
        )
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == opphørtSimulertRevurdering.id } + opphørtSimulertRevurdering,
            ),
            opphørtSimulertRevurdering,
        )
    }
}

fun tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        revurderingsperiode,
        fixedClock,
    ).let {
        it.copy(
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgInnvilgetFormue(
                periode = revurderingsperiode,
                bosituasjon = it.grunnlagsdata.bosituasjon.singleFullstendigOrThrow(),
            ),
        )
    },
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): Pair<Sak, RevurderingTilAttestering.Opphørt> {
    return simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val attestert = revurdering.tilAttestering(
            attesteringsoppgaveId = OppgaveId("attestering"),
            saksbehandler = saksbehandler,
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            fritekstTilBrev = "friteskst",
        ).getOrFail("Feil i oppsett av testdata")

        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == attestert.id } + attestert,
            ),
            attestert,
        )
    }
}

fun iverksattRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        revurderingsperiode,
        fixedClock,
    ).let {
        it.copy(
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgInnvilgetFormue(
                periode = revurderingsperiode,
                bosituasjon = it.grunnlagsdata.bosituasjon.singleFullstendigOrThrow(),
            ),
        )
    },
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    attestering: Attestering.Iverksatt = attesteringIverksatt,
): Pair<Sak, IverksattRevurdering.Opphørt> {
    return tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val iverksatt = revurdering.tilIverksatt(
            attestant = attestering.attestant,
            utbetal = { UUID30.randomUUID().right() },
        ).getOrFail("Feil i oppsett av testdata")

        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == iverksatt.id } + iverksatt,
            ),
            iverksatt,
        )
    }
}

fun tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        revurderingsperiode,
        fixedClock,
    ),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler"),
    fritekstTilBrev: String = "",
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): Pair<Sak, RevurderingTilAttestering.Innvilget> {
    return simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val innvilgetRevurderingTilAttestering = revurdering.tilAttestering(
            attesteringsoppgaveId = attesteringsoppgaveId,
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
            forhåndsvarsel = forhåndsvarsel,
        )
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == innvilgetRevurderingTilAttestering.id } + innvilgetRevurderingTilAttestering,
            ),
            innvilgetRevurderingTilAttestering,
        )
    }
}

fun tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    fritekstTilBrev: String = "",
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    skalFøreTilBrevutsending: Boolean = true,
): Pair<Sak, RevurderingTilAttestering.IngenEndring> {
    return beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val innvilgetRevurderingTilAttestering = revurdering.tilAttestering(
            attesteringsoppgaveId = attesteringsoppgaveId,
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
            skalFøreTilBrevutsending = skalFøreTilBrevutsending,
        )
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == innvilgetRevurderingTilAttestering.id } + innvilgetRevurderingTilAttestering,
            ),
            innvilgetRevurderingTilAttestering,
        )
    }
}

fun underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlignsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        revurderingsperiode,
        fixedClock,
    ),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler"),
    fritekstTilBrev: String = "",
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
    attestering: Attestering.Underkjent = Attestering.Underkjent(
        attestant = NavIdentBruker.Attestant(navIdent = "attestant"),
        grunn = Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT,
        kommentar = "feil vilkår man",
        opprettet = fixedTidspunkt,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): Pair<Sak, UnderkjentRevurdering> {
    return tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        attesteringsoppgaveId = attesteringsoppgaveId,
        saksbehandler = saksbehandler,
        fritekstTilBrev = fritekstTilBrev,
        forhåndsvarsel = forhåndsvarsel,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val underkjentRevurdering = revurdering.underkjenn(
            attestering = attestering,
            oppgaveId = OppgaveId(value = "oppgaveId"),
        )
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == underkjentRevurdering.id } + underkjentRevurdering,
            ),
            underkjentRevurdering,
        )
    }
}

fun iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        revurderingsperiode,
        fixedClock,
    ),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler"),
    fritekstTilBrev: String = "",
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
    attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant("Attestant"),
    utbetal: () -> Either<RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale, UUID30> = {
        UUID30.randomUUID().right()
    },
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): Pair<Sak, IverksattRevurdering.Innvilget> {
    return tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        attesteringsoppgaveId = attesteringsoppgaveId,
        saksbehandler = saksbehandler,
        fritekstTilBrev = fritekstTilBrev,
        forhåndsvarsel = forhåndsvarsel,
        revurderingsårsak = revurderingsårsak,
    ).let { (sak, revurdering) ->
        val innvilgetIverksattRevurdering = revurdering.tilIverksatt(
            attestant = attestant,
            utbetal = utbetal,
        ).getOrHandle { throw RuntimeException("Feilet med generering av test data for Iverksatt-revurdering") }
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == innvilgetIverksattRevurdering.id } + innvilgetIverksattRevurdering,
            ),
            innvilgetIverksattRevurdering,
        )
    }
}

fun iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler"),
    fritekstTilBrev: String = "",
    attestant: NavIdentBruker.Attestant = no.nav.su.se.bakover.test.attestant,
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    skalFøreTilBrevutsending: Boolean = true,
): Pair<Sak, IverksattRevurdering.IngenEndring> {
    return tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringsoppgaveId = attesteringsoppgaveId,
        saksbehandler = saksbehandler,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        skalFøreTilBrevutsending = skalFøreTilBrevutsending,
    ).let { (sak, revurdering) ->
        val innvilgetIverksattRevurdering = revurdering.tilIverksatt(
            attestant = attestant,
            clock = fixedClock,
        ).getOrHandle { throw RuntimeException("Feilet med generering av test data for Iverksatt-revurdering") }
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == innvilgetIverksattRevurdering.id } + innvilgetIverksattRevurdering,
            ),
            innvilgetIverksattRevurdering,
        )
    }
}

fun avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    begrunnelse: String = "begrunnelsensen",
    fritekst: String? = null,
    datoAvsluttet: LocalDate = LocalDate.now(fixedClock),
): Pair<Sak, AvsluttetRevurdering> {
    return simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().let { (sak, simulert) ->
        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse,
            fritekst = fritekst,
            datoAvsluttet = datoAvsluttet,
        ).getOrHandle { throw RuntimeException("Feilet med generering av test data for Iverksatt-revurdering") }

        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == avsluttet.id } + avsluttet,
            ),
            avsluttet,
        )
    }
}

fun simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
        tilOgMed = periode2021.tilOgMed,
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode, "whatever"),
    ),
    simulering: Simulering = simuleringStans(
        stansDato = periode.fraOgMed,
        eksisterendeUtbetalinger = sakOgVedtakSomKanRevurderes.first.utbetalinger,
    ),
): Pair<Sak, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
    return sakOgVedtakSomKanRevurderes.let { (sak, vedtak) ->
        val revurdering = StansAvYtelseRevurdering.SimulertStansAvYtelse(
            id = revurderingId,
            opprettet = fixedTidspunkt,
            periode = periode,
            grunnlagsdata = vedtak.behandling.grunnlagsdata,
            vilkårsvurderinger = vedtak.behandling.vilkårsvurderinger.tilVilkårsvurderingerRevurdering(),
            tilRevurdering = vedtak,
            saksbehandler = saksbehandler,
            simulering = simulering,
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                begrunnelse = "valid",
            ),
        )

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == revurdering.id } + revurdering,
        ) to revurdering
    }
}

fun iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
        tilOgMed = periode2021.tilOgMed,
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode, "whatever"),
    ),
    attestering: Attestering = attesteringIverksatt,
): Pair<Sak, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
    return simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
    ).let { (sak, simulert) ->
        val iverksatt = simulert.iverksett(attestering)
            .getOrFail("Feil ved oppsett av testdata for iverksatt stans av ytelse")

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == iverksatt.id } + iverksatt,
        ) to iverksatt
    }
}

fun avsluttetStansAvYtelseFraIverksattSøknadsbehandlignsvedtak(
    begrunnelse: String = "begrunnelse for å avslutte stans av ytelse",
    datoAvsluttet: LocalDate = LocalDate.now(fixedClock),
): Pair<Sak, StansAvYtelseRevurdering.AvsluttetStansAvYtelse> {
    return simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().let { (sak, simulert) ->

        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse, datoAvsluttet = datoAvsluttet,
        ).getOrFail("Feil ved oppsett av test-data for avsluttet stans av ytelse")

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == avsluttet.id } + avsluttet,
        ) to avsluttet
    }
}

fun simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
    periodeForStans: Periode = Periode.create(
        fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
        tilOgMed = periode2021.tilOgMed,
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periodeForStans,
    ),
    simulering: Simulering = simuleringGjenopptak(
        eksisterendeUtbetalinger = sakOgVedtakSomKanRevurderes.first.utbetalinger,
        fnr = sakOgVedtakSomKanRevurderes.first.fnr,
        sakId = sakOgVedtakSomKanRevurderes.first.id,
        saksnummer = sakOgVedtakSomKanRevurderes.first.saksnummer,
    ),
): Pair<Sak, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse> {
    return sakOgVedtakSomKanRevurderes.let { (sak, vedtak) ->
        val revurdering = GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
            id = revurderingId,
            opprettet = fixedTidspunkt,
            periode = vedtak.periode,
            grunnlagsdata = vedtak.behandling.grunnlagsdata,
            vilkårsvurderinger = vedtak.behandling.vilkårsvurderinger.tilVilkårsvurderingerRevurdering(),
            tilRevurdering = vedtak,
            saksbehandler = saksbehandler,
            simulering = simulering,
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                begrunnelse = "valid",
            ),
        )
        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == revurdering.id } + revurdering,
        ) to revurdering
    }
}

fun iverksattGjenopptakelseAvytelseFraVedtakStansAvYtelse(
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
        tilOgMed = periode2021.tilOgMed,
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
    ),
    attestering: Attestering = attesteringIverksatt,
): Pair<Sak, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
    return simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
        periodeForStans = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
    ).let { (sak, simulert) ->
        val iverksatt = simulert.iverksett(attestering)
            .getOrFail("Feil i oppsett for testdata")
        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == iverksatt.id } + iverksatt,
        ) to iverksatt
    }
}

fun avsluttetGjenopptakelseAvYtelseeFraIverksattSøknadsbehandlignsvedtak(
    begrunnelse: String = "begrunnelse for å avslutte stans av ytelse",
    datoAvsluttet: LocalDate = LocalDate.now(fixedClock),
): Pair<Sak, GjenopptaYtelseRevurdering.AvsluttetGjenoppta> {
    return simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse().let { (sak, simulert) ->

        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse, datoAvsluttet = datoAvsluttet,
        ).getOrFail("Feil ved oppsett av test-data for avsluttet stans av ytelse")

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == avsluttet.id } + avsluttet,
        ) to avsluttet
    }
}
