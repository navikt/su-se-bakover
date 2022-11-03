package no.nav.su.se.bakover.test

import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
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
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkår
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
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    clock: Clock = tikkendeFixedClock,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        periode = revurderingsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    avkorting: AvkortingVedRevurdering.Uhåndtert = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
): Pair<Sak, OpprettetRevurdering> {
    require(sakOgVedtakSomKanRevurderes.first.vedtakListe.contains(sakOgVedtakSomKanRevurderes.second)) {
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
        tilRevurdering = sakOgVedtakSomKanRevurderes.second.id,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveIdRevurdering,
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = null,
        grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
        vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.tilVilkårsvurderingerRevurdering(),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
        avkorting = avkorting,
        sakinfo = sakOgVedtakSomKanRevurderes.first.info(),
    )
    return Pair(
        sakOgVedtakSomKanRevurderes.first.copy(
            // For å støtte revurderinger av revurderinger (burde nok legge inn litt validering)
            revurderinger = sakOgVedtakSomKanRevurderes.first.revurderinger + opprettetRevurdering,
        ),
        opprettetRevurdering,
    )
}

fun opprettRevurderingFraSaksopplysninger(
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes>,
    clock: Clock = tikkendeFixedClock,
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    avkorting: AvkortingVedRevurdering.Uhåndtert = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
): Pair<Sak, OpprettetRevurdering> {
    val gjeldendeVedtaksdata = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVedtaksdata(
        periode = revurderingsperiode,
        clock = clock,
    ).getOrFail()

    val gjeldendeVedtak = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(revurderingsperiode.fraOgMed)
        ?: throw IllegalStateException("Fant ingen gjeldende vedtak for fra og med dato for revurderingen: ${revurderingsperiode.fraOgMed}")

    val grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
        grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
        vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
    ).let {
        GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = grunnlagsdataOverrides.fold(it.grunnlagsdata) { acc, grunnlag ->
                when (grunnlag) {
                    is Grunnlag.Bosituasjon -> {
                        acc.copy(bosituasjon = (acc.bosituasjon + grunnlag) - it.grunnlagsdata.bosituasjon.toSet())
                    }

                    is Grunnlag.Fradragsgrunnlag -> {
                        acc.copy(fradragsgrunnlag = (acc.fradragsgrunnlag + grunnlag) - it.grunnlagsdata.fradragsgrunnlag.toSet())
                    }

                    else -> {
                        // andre grunnlag legges til via sine respektive vilkår
                        acc
                    }
                }
            },
            vilkårsvurderinger = vilkårOverrides.fold(it.vilkårsvurderinger) { acc, vilkår -> acc.leggTil(vilkår) },
        )
    }
    // TODO refaktorer slik at vi ikke får til å opprette med mismatch mellom periode og gjeldende data
    val opprettetRevurdering = OpprettetRevurdering(
        id = UUID.randomUUID(),
        periode = revurderingsperiode,
        opprettet = Tidspunkt.now(clock),
        tilRevurdering = gjeldendeVedtak.id,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveIdRevurdering,
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = null,
        grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
        vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.tilVilkårsvurderingerRevurdering(),
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = Attesteringshistorikk.empty(),
        avkorting = avkorting,
        sakinfo = sakOgVedtakSomKanRevurderes.first.info(),
    )
    return Pair(
        sakOgVedtakSomKanRevurderes.first.copy(
            // For å støtte revurderinger av revurderinger (burde nok legge inn litt validering)
            revurderinger = sakOgVedtakSomKanRevurderes.first.revurderinger + opprettetRevurdering,
        ),
        opprettetRevurdering,
    )
}

fun opprettetRevurdering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    clock: Clock = tikkendeFixedClock,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    avkorting: AvkortingVedRevurdering.Uhåndtert = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
): Pair<Sak, OpprettetRevurdering> {
    return opprettRevurderingFraSaksopplysninger(
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        revurderingsårsak = revurderingsårsak,
        avkorting = avkorting,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
    )
}

fun beregnetRevurdering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    clock: Clock = tikkendeFixedClock,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
): Pair<Sak, BeregnetRevurdering> {
    return opprettetRevurdering(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        revurderingsårsak = revurderingsårsak,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
    ).let { (sak, opprettet) ->
        val beregnet = opprettet.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
            gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                fraOgMed = revurderingsperiode.fraOgMed,
                clock = clock,
            ).getOrFail(),
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail()

        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == beregnet.id } + beregnet,
        ) to beregnet
    }
}

fun simulertRevurdering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    clock: Clock = tikkendeFixedClock,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    forhåndsvarsel: Forhåndsvarsel? = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Pair<Sak, SimulertRevurdering> {
    return beregnetRevurdering(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
        clock = clock,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
    ).let { (sak, beregnet) ->
        val simulert = when (beregnet) {
            is BeregnetRevurdering.IngenEndring -> {
                throw IllegalStateException("Kan ikke simulere for:${beregnet::class}, overstyr vilkår/grunnlag for et annet resultat.")
            }

            is BeregnetRevurdering.Innvilget -> {
                val simulert = beregnet.simuler(
                    saksbehandler = saksbehandler,
                    clock = clock,
                    simuler = { _, _ ->
                        simulerUtbetaling(
                            sak = sak,
                            revurdering = beregnet,
                            simuleringsperiode = beregnet.periode,
                            clock = clock,
                            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                        ).map {
                            it.simulering
                        }
                    },
                ).getOrFail()
                oppdaterTilbakekrevingsbehandling(simulert)
            }

            is BeregnetRevurdering.Opphørt -> {
                val simulert = beregnet.simuler(
                    saksbehandler = saksbehandler,
                    clock = clock,
                    simuler = { periode, saksbehandler ->
                        simulerOpphør(
                            sak = sak,
                            revurdering = beregnet,
                            simuleringsperiode = periode,
                            behandler = saksbehandler,
                            clock = clock,
                            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                        )
                    },
                ).getOrFail()
                oppdaterTilbakekrevingsbehandling(simulert)
            }
        }.prøvÅLeggTilForhåndsvarselPåSimulertRevurdering(forhåndsvarsel)

        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == simulert.id } + simulert,
        ) to simulert
    }
}

fun revurderingTilAttestering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    clock: Clock = tikkendeFixedClock,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
    fritekstTilBrev: String = "fritekstTilBrev",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Pair<Sak, RevurderingTilAttestering> {
    return simulertRevurdering(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        revurderingsårsak = revurderingsårsak,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
        forhåndsvarsel = forhåndsvarsel,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { (sak, simulert) ->
        val tilAttestering = when (simulert) {
            is SimulertRevurdering.Innvilget -> {
                val oppdatertTilbakekreving =
                    (oppdaterTilbakekrevingsbehandling(simulert) as SimulertRevurdering.Innvilget)

                oppdatertTilbakekreving.tilAttestering(
                    attesteringsoppgaveId = attesteringsoppgaveId,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = fritekstTilBrev,
                ).getOrFail()
            }

            is SimulertRevurdering.Opphørt -> {
                val oppdatertTilbakekreving =
                    (oppdaterTilbakekrevingsbehandling(simulert) as SimulertRevurdering.Opphørt)

                oppdatertTilbakekreving.tilAttestering(
                    attesteringsoppgaveId = attesteringsoppgaveId,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = fritekstTilBrev,
                ).getOrFail()
            }
        }
        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == tilAttestering.id } + tilAttestering,
        ) to tilAttestering
    }
}

fun revurderingUnderkjent(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    clock: Clock = tikkendeFixedClock,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
    attestering: Attestering.Underkjent = attesteringUnderkjent(clock),
    fritekstTilBrev: String = "fritekstTilBrev",
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Pair<Sak, UnderkjentRevurdering> {
    return revurderingTilAttestering(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
        clock = clock,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
        forhåndsvarsel = forhåndsvarsel,
        fritekstTilBrev = fritekstTilBrev,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { (sak, tilAttestering) ->
        val underkjent = tilAttestering.underkjenn(
            attestering = attestering,
            oppgaveId = OppgaveId("underkjentOppgaveId"),
        )
        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == tilAttestering.id } + underkjent,
        ) to underkjent
    }
}

private fun oppdaterTilbakekrevingsbehandling(revurdering: SimulertRevurdering): SimulertRevurdering {
    return when (revurdering.simulering.harFeilutbetalinger()) {
        true -> {
            revurdering.oppdaterTilbakekrevingsbehandling(
                tilbakekrevingsbehandling = Tilbakekrev(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    sakId = revurdering.sakId,
                    revurderingId = revurdering.id,
                    periode = revurdering.periode,
                ),
            )
        }

        false -> {
            revurdering.oppdaterTilbakekrevingsbehandling(
                tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
            )
        }
    }
}

fun iverksattRevurdering(
    clock: Clock = tikkendeFixedClock,
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
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
    attestering: Attestering = attesteringIverksatt(clock),
    fritekstTilBrev: String = "fritekstTilBrev",
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Triple<Sak, IverksattRevurdering, Utbetaling?> {
    return revurderingTilAttestering(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        revurderingsårsak = revurderingsårsak,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
        forhåndsvarsel = forhåndsvarsel,
        fritekstTilBrev = fritekstTilBrev,
        saksbehandler = saksbehandler,
        attesteringsoppgaveId = attesteringsoppgaveId,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { (sak, tilAttestering) ->
        val (iverksatt, utbetaling) = tilAttestering.tilIverksatt(
            attestant = attestering.attestant,
            clock = clock,
            hentOpprinneligAvkorting = {
                when (val opprinnelig = tilAttestering.avkorting) {
                    is AvkortingVedRevurdering.Håndtert.AnnullerUtestående -> {
                        opprinnelig.avkortingsvarsel
                    }

                    AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående -> {
                        null
                    }

                    is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres -> {
                        null
                    }

                    is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
                        null
                    }

                    is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                        opprinnelig.annullerUtestående
                    }
                }
            },
        ).getOrFail() to when (tilAttestering) {
            is RevurderingTilAttestering.IngenEndring -> {
                null
            }

            is RevurderingTilAttestering.Innvilget -> {
                simulerUtbetaling(
                    sak = sak,
                    revurdering = tilAttestering,
                    simuleringsperiode = tilAttestering.periode,
                    behandler = attestering.attestant,
                    clock = clock,
                    utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                ).getOrFail().let {
                    it.toOversendtUtbetaling(UtbetalingStub.generateRequest(it))
                }
            }

            is RevurderingTilAttestering.Opphørt -> {
                simulerOpphør(
                    sak = sak,
                    revurdering = tilAttestering,
                    simuleringsperiode = tilAttestering.opphørsperiodeForUtbetalinger,
                    behandler = attestering.attestant,
                    clock = clock,
                    utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                ).getOrFail().let {
                    it.toOversendtUtbetaling(UtbetalingStub.generateRequest(it))
                }
            }
        }
        Triple(
            first = sak.copy(
                revurderinger = sak.revurderinger.filterNot { it.id == iverksatt.id } + iverksatt,
                utbetalinger = if (utbetaling != null) sak.utbetalinger + utbetaling else sak.utbetalinger,
                uteståendeAvkorting = when (val avkorting = iverksatt.avkorting) {
                    is AvkortingVedRevurdering.Iverksatt.AnnullerUtestående -> Avkortingsvarsel.Ingen
                    is AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående -> Avkortingsvarsel.Ingen
                    is AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres -> Avkortingsvarsel.Ingen
                    is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel -> avkorting.avkortingsvarsel
                    is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> avkorting.avkortingsvarsel
                },
            ),
            second = iverksatt,
            third = utbetaling,
        )
    }
}

fun vedtakRevurdering(
    clock: Clock = tikkendeFixedClock,
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
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
    attestering: Attestering = attesteringIverksatt(clock),
    fritekstTilBrev: String = "fritekstTilBrev",
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Pair<Sak, VedtakSomKanRevurderes> {
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
        forhåndsvarsel = forhåndsvarsel,
        attestering = attestering,
        fritekstTilBrev = fritekstTilBrev,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { (sak, iverksatt, utbetaling) ->
        val vedtak = when (iverksatt) {
            is IverksattRevurdering.IngenEndring -> {
                VedtakSomKanRevurderes.from(iverksatt, clock)
            }

            is IverksattRevurdering.Innvilget -> {
                VedtakSomKanRevurderes.from(iverksatt, utbetaling!!.id, clock)
            }

            is IverksattRevurdering.Opphørt -> {
                VedtakSomKanRevurderes.from(iverksatt, utbetaling!!.id, clock)
            }
        }

        sak.copy(
            vedtakListe = sak.vedtakListe + vedtak,
        ) to vedtak
    }
}

/**
 * En innvilget beregnet revurdering med utgangspunkt i et vedtak fra en innvilget søknadsbehandling.
 */
fun beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    clock: Clock = fixedClock,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = innvilgetGrunnlagsdataOgVilkårsvurderinger(
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsperiode = revurderingsperiode,
        clock = clock,
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
        val innvilgetBeregnetRevurdering = revurdering.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
            gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                fraOgMed = revurderingsperiode.fraOgMed,
                clock = clock,
            ).getOrFail(),
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail() as BeregnetRevurdering.Innvilget
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
    fradrag = FradragFactory.nyFradragsperiode(
        fradragstype = type,
        månedsbeløp = månedsbeløp,
        periode = periode,
        utenlandskInntekt = utenlandskInntekt,
        tilhører = tilhører,
    ),
).getOrFail()

/** revurderingsårsak låses til GRUNNBELØP_REGULERING siden det er den eneste årsaken som kan føre til ingen endring. */
fun beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    clock: Clock = fixedClock,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
        periode = revurderingsperiode,
        clock = clock,
    ),
    eksisterendeUtbetalinger: List<Utbetaling> = sakOgVedtakSomKanRevurderes.first.utbetalinger,
): Pair<Sak, BeregnetRevurdering.IngenEndring> {
    return opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        revurderingsårsak = Revurderingsårsak(
            Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
            Revurderingsårsak.Begrunnelse.create("testdata - ingen endring"),
        ),
    ).let { (sak, revurdering) ->
        val innvilgetBeregnetRevurdering = revurdering.beregn(
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            clock = clock,
            gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                fraOgMed = revurderingsperiode.fraOgMed,
                clock = clock,
            ).getOrFail(),
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail() as BeregnetRevurdering.IngenEndring
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

// legger til grunnlaget som allerede finnes pluss et fradrag slik at revurdering blir en endring og ikke ingenEndring
fun innvilgetGrunnlagsdataOgVilkårsvurderinger(
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes>,
    revurderingsperiode: Periode,
    clock: Clock = fixedClock,
): GrunnlagsdataOgVilkårsvurderinger.Revurdering = sakOgVedtakSomKanRevurderes.first.hentGjeldendeVilkårOgGrunnlag(
    periode = revurderingsperiode,
    clock = clock,
).let {
    it.copy(
        grunnlagsdata = it.grunnlagsdata.copy(
            fradragsgrunnlag = it.grunnlagsdata.fradragsgrunnlag +
                nonEmptyListOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = revurderingsperiode,
                        arbeidsinntekt = 7500.0,
                    ),
                ),
        ),
    )
}

/**
 * En innvilget simulert revurdering med utgangspunkt i et vedtak fra en innvilget søknadsbehandling.
 */
fun simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    clock: Clock = tikkendeFixedClock,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = innvilgetGrunnlagsdataOgVilkårsvurderinger(
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsperiode = revurderingsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    forhåndsvarsel: Forhåndsvarsel? = null,
    tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling = IkkeBehovForTilbakekrevingUnderBehandling,
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
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
        val innvilgetSimulertRevurdering = revurdering.simuler(
            saksbehandler = saksbehandler,
            clock = clock,
            simuler = { _, _ ->
                simulerUtbetaling(
                    sak = sak,
                    revurdering = revurdering,
                    simuleringsperiode = revurdering.periode,
                    behandler = revurdering.saksbehandler,
                    clock = clock,
                    utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                ).getOrFail().simulering.right()
            },
        ).getOrFail().prøvÅLeggTilForhåndsvarselPåSimulertRevurdering(
            forhåndsvarsel = forhåndsvarsel,
        ).oppdaterTilbakekrevingsbehandling(
            tilbakekrevingsbehandling = tilbakekrevingsbehandling,
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

private fun <T : SimulertRevurdering> T.prøvÅLeggTilForhåndsvarselPåSimulertRevurdering(
    forhåndsvarsel: Forhåndsvarsel?,
): T {
    // SimulertRevurdering er første tilstanden forhåndsvarsel kan velges og den skal initielt være null her.
    // Den kan jo ha blitt tatt tilbake til et tidligere steg, men da bør man følge stegene for at modellen skal bli mest mulig riktig.
    assert(this.forhåndsvarsel == null)
    @Suppress("UNCHECKED_CAST")
    return when (forhåndsvarsel) {
        is Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet -> {
            throw IllegalArgumentException("Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet skal ikke brukes på denne måten lenger.")
        }

        is Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget -> {
            this.markerForhåndsvarselSomSendt().getOrFail()
                .prøvOvergangTilEndreGrunnlaget(forhåndsvarsel.begrunnelse).getOrFail()
        }

        is Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag -> {
            this.markerForhåndsvarselSomSendt().getOrFail()
                .prøvOvergangTilFortsettMedSammeGrunnlag(forhåndsvarsel.begrunnelse)
                .getOrFail()
        }

        Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles -> {
            this.ikkeSendForhåndsvarsel().getOrFail()
        }

        Forhåndsvarsel.UnderBehandling.Sendt -> {
            this.markerForhåndsvarselSomSendt().getOrFail()
        }

        null -> this
    } as T
}

fun tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    clock: Clock = tikkendeFixedClock,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = innvilgetGrunnlagsdataOgVilkårsvurderinger(
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsperiode = revurderingsperiode,
        clock = clock,
    ),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    fritekstTilBrev: String = "",
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
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
        forhåndsvarsel = forhåndsvarsel,
        clock = clock,
    ).let { (sak, revurdering) ->
        val innvilgetRevurderingTilAttestering = revurdering.tilAttestering(
            attesteringsoppgaveId = attesteringsoppgaveId,
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
        ).getOrFail()
        Pair(
            sak.copy(
                // Erstatter den gamle versjonen av samme revurderinger.
                revurderinger = sak.revurderinger.filterNot { it.id == innvilgetRevurderingTilAttestering.id } + innvilgetRevurderingTilAttestering,
            ),
            innvilgetRevurderingTilAttestering,
        )
    }
}

/** revurderingsårsak låses til GRUNNBELØP_REGULERING siden det er den eneste årsaken som kan føre til ingen endring. */
fun tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    fritekstTilBrev: String = "",
    skalFøreTilBrevutsending: Boolean = true,
): Pair<Sak, RevurderingTilAttestering.IngenEndring> {
    return beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
    ).let { (sak, revurdering) ->
        val innvilgetRevurderingTilAttestering = revurdering.tilAttestering(
            attesteringsoppgaveId = attesteringsoppgaveId,
            saksbehandler = saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
            skalFøreTilUtsendingAvVedtaksbrev = skalFøreTilBrevutsending,
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

fun underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    clock: Clock = tikkendeFixedClock,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = innvilgetGrunnlagsdataOgVilkårsvurderinger(
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsperiode = revurderingsperiode,
        clock = clock,
    ),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    fritekstTilBrev: String = "",
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
    attestering: Attestering.Underkjent = Attestering.Underkjent(
        attestant = attestant,
        grunn = Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT,
        kommentar = "feil vilkår man",
        opprettet = fixedTidspunkt,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
): Pair<Sak, UnderkjentRevurdering.Innvilget> {
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
            underkjentRevurdering as UnderkjentRevurdering.Innvilget,
        )
    }
}

fun iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
    ),
    clock: Clock = tikkendeFixedClock,
    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger = innvilgetGrunnlagsdataOgVilkårsvurderinger(
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsperiode = revurderingsperiode,
        clock = clock,
    ),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    fritekstTilBrev: String = "",
    forhåndsvarsel: Forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
    attestant: NavIdentBruker.Attestant = no.nav.su.se.bakover.test.attestant,
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
            clock = clock,
            hentOpprinneligAvkorting = { null },
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

/** revurderingsårsak låses til GRUNNBELØP_REGULERING siden det er den eneste årsaken som kan føre til ingen endring. */
fun iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    fritekstTilBrev: String = "",
    attestant: NavIdentBruker.Attestant = no.nav.su.se.bakover.test.attestant,
    skalFøreTilBrevutsending: Boolean = true,
    clock: Clock = fixedClock,
): Pair<Sak, IverksattRevurdering.IngenEndring> {
    return tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringsoppgaveId = attesteringsoppgaveId,
        saksbehandler = saksbehandler,
        fritekstTilBrev = fritekstTilBrev,
        skalFøreTilBrevutsending = skalFøreTilBrevutsending,
    ).let { (sak, revurdering) ->
        val innvilgetIverksattRevurdering = revurdering.tilIverksatt(
            attestant = attestant,
            clock = clock,
            hentOpprinneligAvkorting = { null },
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
    tidspunktAvsluttet: Tidspunkt = Tidspunkt.now(fixedClock),
): Pair<Sak, AvsluttetRevurdering> {
    return simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().let { (sak, simulert) ->
        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse,
            brevvalg = fritekst?.let { Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst(it) },
            tidspunktAvsluttet = tidspunktAvsluttet,
        ).getOrFail()

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
    clock: Clock = TikkendeKlokke(1.januar(2021).fixedClock()),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode),
        clock = clock,
    ),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Pair<Sak, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
    return sakOgVedtakSomKanRevurderes.let { (sak, vedtak) ->
        val revurdering = StansAvYtelseRevurdering.SimulertStansAvYtelse(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            periode = periode,
            grunnlagsdata = sak.kopierGjeldendeVedtaksdata(periode.fraOgMed, clock).getOrFail().grunnlagsdata,
            vilkårsvurderinger = sak.kopierGjeldendeVedtaksdata(periode.fraOgMed, clock).getOrFail().vilkårsvurderinger,
            tilRevurdering = vedtak.id,
            saksbehandler = saksbehandler,
            simulering = simulerStans(
                sak = sakOgVedtakSomKanRevurderes.first,
                stans = null,
                stansDato = periode.fraOgMed,
                behandler = saksbehandler,
                clock = clock,
                utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
            ).getOrFail().simulering,
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                begrunnelse = "valid",
            ),
            sakinfo = sak.info(),
        )

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == revurdering.id } + revurdering,
        ) to revurdering
    }
}

fun iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    clock: Clock = TikkendeKlokke(1.januar(2021).fixedClock()),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode),
        clock = clock,
    ),
    attestering: Attestering = attesteringIverksatt(clock),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Pair<Sak, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
    return simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { (sak, simulert) ->
        val iverksatt = simulert.iverksett(attestering).getOrFail()

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == iverksatt.id } + iverksatt,
        ) to iverksatt
    }
}

fun avsluttetStansAvYtelseFraIverksattSøknadsbehandlignsvedtak(
    clock: Clock = TikkendeKlokke(1.januar(2021).fixedClock()),
    begrunnelse: String = "begrunnelse for å avslutte stans av ytelse",
    tidspunktAvsluttet: Tidspunkt = Tidspunkt.now(clock),
): Pair<Sak, StansAvYtelseRevurdering.AvsluttetStansAvYtelse> {
    return simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(clock).let { (sak, simulert) ->
        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
        ).getOrFail()

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == avsluttet.id } + avsluttet,
        ) to avsluttet
    }
}

fun simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
    clock: Clock = TikkendeKlokke(1.januar(2021).fixedClock()),
    periodeForStans: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periodeForStans,
        clock = clock,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ),
): Pair<Sak, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse> {
    require(sakOgVedtakSomKanRevurderes.first.vedtakListe.last() is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse)

    return sakOgVedtakSomKanRevurderes.let { (sak, vedtak) ->
        val revurdering = GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
            id = revurderingId,
            opprettet = Tidspunkt.now(clock),
            periode = vedtak.periode,
            grunnlagsdata = vedtak.behandling.grunnlagsdata,
            vilkårsvurderinger = vedtak.behandling.vilkårsvurderinger.tilVilkårsvurderingerRevurdering(),
            tilRevurdering = vedtak.id,
            saksbehandler = saksbehandler,
            simulering = simulerGjenopptak(
                sak = sak,
                gjenopptak = null,
                behandler = saksbehandler,
                clock = clock,
            ).getOrFail().simulering,
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                begrunnelse = "valid",
            ),
            sakinfo = sak.info(),
        )
        sak.copy(
            revurderinger = sak.revurderinger.filterNot { it.id == revurdering.id } + revurdering,
        ) to revurdering
    }
}

/**
 * @param sakOgVedtakSomKanRevurderes Dersom denne ikke sendes inn vil det opprettes 2 vedtak. Der:
 * 1) søknadbehandlingsvedtaket får clock+0,
 * 2) Stansvedtaket får clock+1
 *
 * [GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse] vil få clock+2
 */
fun iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse(
    clock: Clock = TikkendeKlokke(1.januar(2021).fixedClock()),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        clock = clock,
    ),
    attestering: Attestering = attesteringIverksatt(clock),
): Pair<Sak, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
    return simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
        periodeForStans = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
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
    tidspunktAvsluttet: Tidspunkt = Tidspunkt.now(fixedClock),
): Pair<Sak, GjenopptaYtelseRevurdering.AvsluttetGjenoppta> {
    return simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse().let { (sak, simulert) ->
        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
        ).getOrFail()

        sak.copy(
            // Erstatter den gamle versjonen av samme revurderinger.
            revurderinger = sak.revurderinger.filterNot { it.id == avsluttet.id } + avsluttet,
        ) to avsluttet
    }
}
