package no.nav.su.se.bakover.test

import arrow.core.Either
import arrow.core.getOrElse
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
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.oppdaterBosituasjonsperiode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.oppdaterFradragsperiode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
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
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakFelles
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
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
    clock: Clock = fixedClock,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderingerForInnvilgetRevurderingUtenFradrag(
        tilRevurdering = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
            periode = revurderingsperiode,
            clock = clock,
        ),
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
        vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
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
    clock: Clock = fixedClock,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderingerForInnvilgetRevurderingMedFradrag(
        periode = stønadsperiode.periode,
        basertPå = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
            revurderingsperiode,
            fixedClock,
        ),
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
        clock = clock,
    ).let { (sak, revurdering) ->
        val innvilgetBeregnetRevurdering =
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                utgangspunkt = sak.hentGjeldendeMånedsberegningForEnkeltmåned(revurderingsperiode.månedenFør())
                    .getOrElse { null },
            ).orNull() as BeregnetRevurdering.Innvilget
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
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
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
        val innvilgetBeregnetRevurdering = revurdering.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            utgangspunkt = sak.hentGjeldendeMånedsberegningForEnkeltmåned(revurderingsperiode.månedenFør())
                .getOrElse { null }
        ).orNull() as BeregnetRevurdering.IngenEndring
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == innvilgetBeregnetRevurdering.id } + innvilgetBeregnetRevurdering,
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
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderingerForInnvilgetRevurderingMedFradrag(
        periode = stønadsperiode.periode,
        basertPå = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
            revurderingsperiode,
            fixedClock,
        ),
    ).let {
        it.copy(
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgInnvilgetFormue(
                periode = stønadsperiode.periode,
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
        val opphørtBeregnetRevurdering = revurdering.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            utgangspunkt = sak.hentGjeldendeMånedsberegningForEnkeltmåned(revurderingsperiode.månedenFør())
                .getOrElse { null }
        )
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
    clock: Clock = fixedClock,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderingerForInnvilgetRevurderingMedFradrag(
        periode = stønadsperiode.periode,
        basertPå = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
            periode = revurderingsperiode,
            clock = clock,
        ),
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
        clock = clock,
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

fun grunnlagsdataOgVilkårsvurderingerForInnvilgetRevurderingMedFradrag(
    periode: Periode,
    basertPå: GrunnlagsdataOgVilkårsvurderinger,
): GrunnlagsdataOgVilkårsvurderinger {
    return basertPå.copy(
        grunnlagsdata = basertPå.grunnlagsdata.copy(
            fradragsgrunnlag = basertPå.grunnlagsdata.fradragsgrunnlag + lagFradragsgrunnlag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 10000.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ),
    )
}

fun grunnlagsdataOgVilkårsvurderingerForInnvilgetRevurderingUtenFradrag(
    tilRevurdering: GrunnlagsdataOgVilkårsvurderinger,
): GrunnlagsdataOgVilkårsvurderinger {
    return tilRevurdering.copy(
        grunnlagsdata = tilRevurdering.grunnlagsdata.copy(
            fradragsgrunnlag = emptyList(),
        ),
    )
}

fun grunnlagsdataOgVilkårsvurderinger(
    periode: Periode,
    uføreVilkår: Vilkår.Uførhet = innvilgetUførevilkår(),
    fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = emptyList(),
    bosituasjonsgrunnlag: List<Grunnlag.Bosituasjon.Fullstendig> = listOf(bosituasjongrunnlagEnslig()),
    formueVilkår: Vilkår.Formue = formuevilkårUtenEps0Innvilget(
        periode = periode,
        bosituasjon = bosituasjonsgrunnlag.first(),
    ),
): GrunnlagsdataOgVilkårsvurderinger {
    return GrunnlagsdataOgVilkårsvurderinger(
        grunnlagsdata = Grunnlagsdata.create(
            fradragsgrunnlag = fradragsgrunnlag.oppdaterFradragsperiode(periode).getOrFail(),
            bosituasjon = bosituasjonsgrunnlag.oppdaterBosituasjonsperiode(periode),
        ),
        vilkårsvurderinger = Vilkårsvurderinger(
            uføre = uføreVilkår.oppdaterStønadsperiode(Stønadsperiode.create(periode, "b")),
            formue = formueVilkår.oppdaterStønadsperiode(Stønadsperiode.create(periode, "b")),
        ),
    )
}

fun
simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderingerForInnvilgetRevurderingMedFradrag(
        periode = stønadsperiode.periode,
        basertPå = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
            revurderingsperiode,
            fixedClock,
        ).let {
            it.copy(
                vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgInnvilgetFormue(
                    periode = stønadsperiode.periode,
                    bosituasjon = it.grunnlagsdata.bosituasjon.singleFullstendigOrThrow(),
                ),
            )
        },
    ),
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

fun tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    clock: Clock = fixedClock,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderingerForInnvilgetRevurderingMedFradrag(
        periode = stønadsperiode.periode,
        basertPå = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
            revurderingsperiode,
            fixedClock,
        ),
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
        clock = clock,
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
    clock: Clock = fixedClock,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderingerForInnvilgetRevurderingMedFradrag(
        periode = stønadsperiode.periode,
        basertPå = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
            periode = revurderingsperiode,
            clock = clock,
        ),
    ),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler"),
    fritekstTilBrev: String = "",
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
    attestering: Attestering.Underkjent = Attestering.Underkjent(
        attestant = attestant,
        grunn = Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT,
        kommentar = "feil vilkår man",
        opprettet = Tidspunkt.now(clock),
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
        clock = clock,
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
    clock: Clock = fixedClock,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderingerForInnvilgetRevurderingMedFradrag(
        periode = stønadsperiode.periode,
        basertPå = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
            periode = revurderingsperiode,
            clock = clock,
        ),
    ),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler"),
    fritekstTilBrev: String = "",
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
    attestant: NavIdentBruker.Attestant = no.nav.su.se.bakover.test.attestant,
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
        clock = clock,
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

fun vedtakIngenEndringFraInnvilgetSøknadsbehandlingsvedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = periode2021,
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler("Saksbehandler"),
    fritekstTilBrev: String = "",
    attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant("Attestant"),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    skalFøreTilBrevutsending: Boolean = true,
): Pair<Sak, Vedtak.IngenEndringIYtelse> {
    return iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringsoppgaveId = attesteringsoppgaveId,
        saksbehandler = saksbehandler,
        fritekstTilBrev = fritekstTilBrev,
        attestant = attestant,
        revurderingsårsak = revurderingsårsak,
        skalFøreTilBrevutsending = skalFøreTilBrevutsending,
    ).let { (sak, revurdering) ->
        val vedtak = Vedtak.from(revurdering, fixedClock)
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == revurdering.id } + revurdering,
                vedtakListe = sak.vedtakListe + vedtak,
            ),
            vedtak,
        )
    }
}

fun simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    clock: Clock = fixedClock,
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = periode2021.tilOgMed,
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode, "whatever"),
        clock = clock,
    ),
    simulering: Simulering = simuleringStans(
        stansDato = periode.fraOgMed,
        eksisterendeUtbetalinger = sakOgVedtakSomKanRevurderes.first.utbetalinger,
    ),
): Pair<Sak, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
    return sakOgVedtakSomKanRevurderes.let { (sak, vedtak) ->
        val revurdering = StansAvYtelseRevurdering.SimulertStansAvYtelse(
            id = revurderingId,
            opprettet = Tidspunkt.now(clock),
            periode = periode,
            grunnlagsdata = vedtak.behandling.grunnlagsdata,
            vilkårsvurderinger = vedtak.behandling.vilkårsvurderinger,
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
    clock: Clock = fixedClock,
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = periode2021.tilOgMed,
    ),
    attestering: Attestering = attesteringIverksatt,
): Pair<Sak, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
    return simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        clock = clock,
    ).let { (sak, simulert) ->
        val iverksatt = simulert.iverksett(attestering).getOrFail()

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == iverksatt.id } + iverksatt,
        ) to iverksatt
    }
}

fun simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
    clock: Clock = fixedClock,
    periodeForStans: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = periode2021.tilOgMed,
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelse(
        periode = periodeForStans,
    ),
    simulering: Simulering = simuleringGjenopptak(
        eksisterendeUtbetalinger = sakOgVedtakSomKanRevurderes.first.utbetalinger,
        fnr = sakOgVedtakSomKanRevurderes.first.fnr,
        sakId = sakOgVedtakSomKanRevurderes.first.id,
        saksnummer = sakOgVedtakSomKanRevurderes.first.saksnummer,
        clock = clock,
    ),
): Pair<Sak, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse> {
    return vedtakIverksattStansAvYtelse(
        periode = periodeForStans,
        clock = clock,
    ).let { (sak, vedtak) ->
        val revurdering = GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
            id = revurderingId,
            opprettet = Tidspunkt.now(clock),
            periode = vedtak.periode,
            grunnlagsdata = vedtak.behandling.grunnlagsdata,
            vilkårsvurderinger = vedtak.behandling.vilkårsvurderinger,
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
    clock: Clock = fixedClock,
    periode: Periode,
    attestering: Attestering = attesteringIverksatt,
): Pair<Sak, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
    return simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
        periodeForStans = periode,
        clock = clock,
    ).let { (sak, simulert) ->
        val iverksatt = simulert.iverksett(attestering).getOrFail()
        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == iverksatt.id } + iverksatt,
        ) to iverksatt
    }
}
