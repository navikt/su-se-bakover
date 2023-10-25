package no.nav.su.se.bakover.test

import arrow.core.Tuple4
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.brev.Brevvalg
import io.kotest.assertions.fail
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.endOfMonth
import no.nav.su.se.bakover.common.extensions.startOfMonth
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.IverksettRevurderingResponse
import no.nav.su.se.bakover.domain.revurdering.iverksett.innvilg.IverksettInnvilgetRevurderingResponse
import no.nav.su.se.bakover.domain.revurdering.iverksett.iverksettRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.opphør.medUtbetaling.IverksettOpphørtRevurderingMedUtbetalingResponse
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.toVedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.nyRevurdering
import no.nav.su.se.bakover.domain.sak.oppdaterRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Revurderingsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.test.simulering.simulerGjenopptak
import no.nav.su.se.bakover.test.simulering.simulerOpphør
import no.nav.su.se.bakover.test.simulering.simulerStans
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.utbetaling.kvittering
import økonomi.domain.kvittering.Kvittering
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

fun opprettRevurderingFraSaksopplysninger(
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes>,
    clock: Clock = tikkendeFixedClock(),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
): Pair<Sak, OpprettetRevurdering> {
    vilkårOverrides.map { it::class }.let {
        require(it == it.distinct())
    }
    require(vilkårOverrides.none { it.vurdering == Vurdering.Uavklart }) {
        "Man kan ikke sende inn uavklarte vilkår til en revurdering. Den starter som utfylt(innvilget/avslag) også kan man overskrive de med nye vilkår som er innvilget/avslag, men ikke uavklart."
    }
    return sakOgVedtakSomKanRevurderes.first.opprettRevurdering(
        command = OpprettRevurderingCommand(
            sakId = sakOgVedtakSomKanRevurderes.first.id,
            periode = revurderingsperiode,
            årsak = revurderingsårsak.årsak.toString(),
            begrunnelse = revurderingsårsak.begrunnelse.toString(),
            saksbehandler = saksbehandler,
            informasjonSomRevurderes = informasjonSomRevurderes.informasjonSomRevurderes.keys.toList(),
        ),
        clock = clock,
    ).getOrFail().leggTilOppgaveId(oppgaveIdRevurdering).let { (sak, opprettetRevurdering) ->
        opprettetRevurdering.let { or ->
            vilkårOverrides.filterIsInstance<UføreVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterUføreOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<FlyktningVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterFlyktningvilkårOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            grunnlagsdataOverrides.filterIsInstance<Grunnlag.Bosituasjon>().let {
                if (it.isNotEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    or.oppdaterBosituasjonOgMarkerSomVurdert(it as List<Grunnlag.Bosituasjon.Fullstendig>).getOrFail()
                } else {
                    or
                }
            }
        }.let { or ->
            vilkårOverrides.filterIsInstance<FastOppholdINorgeVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterFastOppholdINorgeOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<FormueVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterFormueOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<UtenlandsoppholdVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterUtenlandsoppholdOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            grunnlagsdataOverrides.filterIsInstance<Grunnlag.Fradragsgrunnlag>().let {
                if (it.isNotEmpty()) {
                    or.oppdaterFradragOgMarkerSomVurdert(it).getOrFail()
                } else {
                    or
                }
            }
        }.let { or ->
            vilkårOverrides.filterIsInstance<OpplysningspliktVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterOpplysningspliktOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<LovligOppholdVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterLovligOppholdOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<PersonligOppmøteVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { or ->
            vilkårOverrides.filterIsInstance<InstitusjonsoppholdVilkår.Vurdert>().firstOrNull()?.let {
                or.oppdaterInstitusjonsoppholdOgMarkerSomVurdert(it).getOrFail()
            } ?: or
        }.let { r ->
            sak.oppdaterRevurdering(r) to r
        }
    }
}

/**
 * @param stønadsperiode brukes kun dersom [sakOgVedtakSomKanRevurderes] får default-verdi.
 * @param sakOgVedtakSomKanRevurderes Dersom denne settes, ignoreres [stønadsperiode]
 */
fun opprettetRevurdering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    clock: Clock = tikkendeFixedClock(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
): Pair<Sak, OpprettetRevurdering> {
    return opprettRevurderingFraSaksopplysninger(
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        revurderingsårsak = revurderingsårsak,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
    )
}

fun beregnetRevurdering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    clock: Clock = tikkendeFixedClock(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
): Pair<Sak, BeregnetRevurdering> {
    return opprettetRevurdering(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        informasjonSomRevurderes = informasjonSomRevurderes,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        revurderingsårsak = revurderingsårsak,
        clock = clock,
        vilkårOverrides = vilkårOverrides,
        grunnlagsdataOverrides = grunnlagsdataOverrides,
    ).let { (sak, opprettet) ->
        val beregnet = opprettet.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = clock,
            gjeldendeVedtaksdata = sak.hentGjeldendeVedtaksdata(
                periode = opprettet.periode,
                clock = clock,
            ).getOrFail(),
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail()

        sak.oppdaterRevurdering(beregnet) to beregnet
    }
}

/**
 * @param skalUtsetteTilbakekreving Dersom denne er true, vil vi ignorere [skalTilbakekreve]
 * @param skalTilbakekreve Avgjør saksbehandlervurderingen om bruker forstod eller burde ha forstått (bruker eller NAVs skyld). Dersom [skalUtsetteTilbakekreving] er satt, ignoreres denne.
 */
fun simulertRevurdering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    clock: Clock = tikkendeFixedClock(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
    brevvalg: BrevvalgRevurdering.Valgt = sendBrev(),
    skalUtsetteTilbakekreving: Boolean = false,
    skalTilbakekreve: Boolean = true,
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
            is BeregnetRevurdering.Innvilget -> {
                val simulert = beregnet.simuler(
                    saksbehandler = saksbehandler,
                    clock = clock,
                    skalUtsetteTilbakekreving = skalUtsetteTilbakekreving,
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
                oppdaterTilbakekrevingsbehandling(simulert, skalUtsetteTilbakekreving, skalTilbakekreve)
            }

            is BeregnetRevurdering.Opphørt -> {
                val simulert = beregnet.simuler(
                    saksbehandler = saksbehandler,
                    clock = clock,
                    skalUtsetteTilbakekreving = false,
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
                oppdaterTilbakekrevingsbehandling(simulert, skalUtsetteTilbakekreving, skalTilbakekreve)
            }
        }.leggTilBrevvalg(brevvalg)

        sak.oppdaterRevurdering(simulert) to simulert
    }
}

/**
 * @param skalUtsetteTilbakekreving Dersom denne er true, vil vi ignorere [skalTilbakekreve]
 * @param skalTilbakekreve Avgjør saksbehandlervurderingen om bruker forstod eller burde ha forstått (bruker eller NAVs skyld). Dersom [skalUtsetteTilbakekreving] er satt, ignoreres denne.
 */
fun revurderingTilAttestering(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    clock: Clock = tikkendeFixedClock(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    attesteringsoppgaveId: OppgaveId = OppgaveId("oppgaveid"),
    utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
    brevvalg: BrevvalgRevurdering.Valgt = sendBrev(),
    skalUtsetteTilbakekreving: Boolean = false,
    skalTilbakekreve: Boolean = true,
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
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
        brevvalg = brevvalg,
        skalUtsetteTilbakekreving = skalUtsetteTilbakekreving,
        skalTilbakekreve = skalTilbakekreve,
    ).let { (sak, simulert) ->
        val tilAttestering = when (simulert) {
            is SimulertRevurdering.Innvilget -> {
                simulert.tilAttestering(
                    attesteringsoppgaveId = attesteringsoppgaveId,
                    saksbehandler = saksbehandler,
                ).getOrFail()
            }

            is SimulertRevurdering.Opphørt -> {
                simulert.tilAttestering(
                    attesteringsoppgaveId = attesteringsoppgaveId,
                    saksbehandler = saksbehandler,
                ).getOrFail()
            }
        }
        sak.oppdaterRevurdering(tilAttestering) to tilAttestering
    }
}

fun revurderingUnderkjent(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = år(2021),
    informasjonSomRevurderes: InformasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    clock: Clock = tikkendeFixedClock(),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    ),
    revurderingsårsak: Revurderingsårsak = no.nav.su.se.bakover.test.revurderingsårsak,
    vilkårOverrides: List<Vilkår> = emptyList(),
    grunnlagsdataOverrides: List<Grunnlag> = emptyList(),
    attestering: Attestering.Underkjent = attesteringUnderkjent(clock),
    utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
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
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { (sak, tilAttestering) ->
        val underkjent = tilAttestering.underkjenn(
            attestering = attestering,
            oppgaveId = OppgaveId("underkjentOppgaveId"),
        )
        sak.oppdaterRevurdering(underkjent) to underkjent
    }
}

private fun oppdaterTilbakekrevingsbehandling(
    revurdering: SimulertRevurdering,
    skalUtsetteTilbakekreving: Boolean = false,
    skalTilbakekreve: Boolean = true,
): SimulertRevurdering {
    return when (revurdering.simulering.harFeilutbetalinger() && !skalUtsetteTilbakekreving) {
        true -> {
            when (skalTilbakekreve) {
                true -> revurdering.oppdaterTilbakekrevingsbehandling(
                    tilbakekrevingsbehandling = Tilbakekrev(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        sakId = revurdering.sakId,
                        revurderingId = revurdering.id,
                        periode = revurdering.periode,
                    ),
                )

                false -> revurdering.oppdaterTilbakekrevingsbehandling(
                    tilbakekrevingsbehandling = IkkeTilbakekrev(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        sakId = revurdering.sakId,
                        revurderingId = revurdering.id,
                        periode = revurdering.periode,
                    ),
                )
            }
        }

        false -> {
            revurdering.oppdaterTilbakekrevingsbehandling(
                tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
            )
        }
    }
}

/**
 * @param skalUtsetteTilbakekreving Dersom denne er true, vil vi ignorere [skalTilbakekreve]
 * @param skalTilbakekreve Avgjør saksbehandlervurderingen om bruker forstod eller burde ha forstått (bruker eller NAVs skyld). Dersom [skalUtsetteTilbakekreving] er satt, ignoreres denne.
 */
fun iverksattRevurdering(
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
    skalUtsetteTilbakekreving: Boolean = false,
    skalTilbakekreve: Boolean = true,
    kvittering: Kvittering? = kvittering(clock = clock),
): Tuple4<Sak, IverksattRevurdering, Utbetaling.OversendtUtbetaling, Revurderingsvedtak> {
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
        saksbehandler = saksbehandler,
        attesteringsoppgaveId = attesteringsoppgaveId,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
        brevvalg = brevvalg,
        skalUtsetteTilbakekreving = skalUtsetteTilbakekreving,
        skalTilbakekreve = skalTilbakekreve,
    ).let { (sak, tilAttestering) ->
        sak.iverksettRevurdering(
            revurderingId = tilAttestering.id,
            attestant = attestant,
            clock = clock,
            simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode ->
                simulerUtbetaling(
                    sak = sak,
                    utbetaling = utbetalingForSimulering,
                    simuleringsperiode = periode,
                    clock = clock,
                    utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                )
            },
            lagDokument = {
                Dokument.UtenMetadata.Vedtak(
                    opprettet = Tidspunkt.now(clock),
                    tittel = "TODO: BrevRequesten bør lages i domenet",
                    generertDokument = pdfATom(),
                    generertDokumentJson = "{}",
                ).right()
            },
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail().let { response ->
            /**
             * TODO: se om vi får til noe som oppfører seg som [IverksettRevurderingResponse.ferdigstillIverksettelseITransaksjon]?
             */
            val oversendtUtbetaling =
                response.utbetaling!!.toOversendtUtbetaling(UtbetalingStub.generateRequest(response.utbetaling!!)).let {
                    if (kvittering != null) {
                        it.toKvittertUtbetaling(kvittering)
                    } else {
                        it
                    }
                }

            Tuple4(
                first = response.sak.copy(
                    utbetalinger = response.sak.utbetalinger.let {
                        if (it.none { it.id == oversendtUtbetaling.id }) {
                            // Utbetalingen finnes ikke på saken fra før, så vi legger den til på slutten. Merk at tidspunktet må være nyere enn alle andre.
                            it.plus(oversendtUtbetaling)
                        } else {
                            // Utbetalingen finnes fra før, så vi oppdaterer den.
                            it.map { if (it.id == oversendtUtbetaling.id) oversendtUtbetaling else it }
                        }
                    }.let { Utbetalinger(it) },
                    uteståendeKravgrunnlag = if (response.vedtak.behandling.simulering?.harFeilutbetalinger() == true && skalUtsetteTilbakekreving) {
                        genererKravgrunnlagFraSimulering(
                            saksnummer = response.sak.saksnummer,
                            simulering = response.vedtak.behandling.simulering!!,
                            utbetalingId = response.vedtak.utbetalingId!!,
                            clock = clock,
                        )
                    } else {
                        null
                    },
                ),
                second = when (response) {
                    is IverksettInnvilgetRevurderingResponse -> {
                        response.vedtak.behandling
                    }

                    is IverksettOpphørtRevurderingMedUtbetalingResponse -> {
                        response.vedtak.behandling
                    }

                    else -> TODO("Ukjent implementasjon av ${IverksettRevurderingResponse::class}")
                },
                third = oversendtUtbetaling,
                fourth = response.vedtak,
            )
        }
    }
}

fun vedtakRevurdering(
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
    utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
    brevvalg: BrevvalgRevurdering.Valgt = sendBrev(),
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
        attestant = attestant,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
        brevvalg = brevvalg,
    ).let {
        it.first to it.fourth
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

fun avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
    begrunnelse: String = "begrunnelsensen",
    fritekst: String? = null,
    tidspunktAvsluttet: Tidspunkt = Tidspunkt.now(fixedClock),
    avsluttetAv: NavIdentBruker = saksbehandler,
): Pair<Sak, AvsluttetRevurdering> {
    return simulertRevurdering().let { (sak, simulert) ->
        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse,
            brevvalg = fritekst?.let { Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst(it) },
            tidspunktAvsluttet = tidspunktAvsluttet,
            avsluttetAv = avsluttetAv,
        ).getOrFail()

        Pair(
            sak.oppdaterRevurdering(avsluttet),
            avsluttet,
        )
    }
}

/**
 * @param clock Defaulter til 2021-01-01 (TODO jah: den defaulter vel til måneden etter? why?)
 * @param periode Defaulter til 11 måneder, fra måneden etter clock.
 */
fun simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    clock: Clock = tikkendeFixedClock(),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode),
        clock = clock,
    ),
    utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
): Pair<Sak, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
    return sakOgVedtakSomKanRevurderes.let { (sak, vedtak) ->
        val gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(periode.fraOgMed, clock).getOrFail()
        val revurdering = StansAvYtelseRevurdering.SimulertStansAvYtelse(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            oppdatert = Tidspunkt.now(clock),
            periode = periode,
            grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
            tilRevurdering = vedtak.id,
            vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
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

        sak.nyRevurdering(revurdering) to revurdering
    }
}

fun iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
    clock: Clock = tikkendeFixedClock(),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = Stønadsperiode.create(periode),
        clock = clock,
    ),
    attestering: Attestering = attesteringIverksatt(clock),
    utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
): Pair<Sak, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
    return simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { (sak, simulert) ->
        val iverksatt = simulert.iverksett(attestering).getOrFail()

        sak.oppdaterRevurdering(iverksatt) to iverksatt
    }
}

fun avsluttetStansAvYtelseFraIverksattSøknadsbehandlignsvedtak(
    clock: Clock = tikkendeFixedClock(),
    begrunnelse: String = "begrunnelse for å avslutte stans av ytelse",
    tidspunktAvsluttet: Tidspunkt = Tidspunkt.now(clock),
    avsluttetAv: NavIdentBruker = saksbehandler,
): Pair<Sak, StansAvYtelseRevurdering.AvsluttetStansAvYtelse> {
    return simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(clock).let { (sak, simulert) ->
        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
            avsluttetAv = avsluttetAv,
        ).getOrFail()

        sak.oppdaterRevurdering(avsluttet) to avsluttet
    }
}

fun simulertGjenopptakAvYtelseFraVedtakStansAvYtelse(
    clock: Clock = tikkendeFixedClock(),
    periodeForStans: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periodeForStans,
        clock = clock,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    ).let { it.first to it.second },
    gjenopptaId: UUID = UUID.randomUUID(),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    revurderingsårsak: Revurderingsårsak = Revurderingsårsak.create(
        årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
        begrunnelse = "valid",
    ),
): Pair<Sak, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse> {
    require(sakOgVedtakSomKanRevurderes.first.vedtakListe.last() is VedtakStansAvYtelse)

    // TODO jah: Vi bør ikke replikere så mange linjer med produksjonskode i GjenopptaYtelseServiceImpl. Vi bør flytte domenekoden fra nevnte fil og kun beholde sideeffektene i servicen.
    return sakOgVedtakSomKanRevurderes.let { (sak, _) ->
        val sisteVedtakPåTidslinje = sak.vedtakstidslinje()?.lastOrNull() ?: fail("Fant ingen vedtak")

        if (sisteVedtakPåTidslinje.originaltVedtak !is VedtakStansAvYtelse) {
            fail("Siste vedtak er ikke stans")
        }
        val gjeldendeVedtaksdata: GjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
            fraOgMed = sisteVedtakPåTidslinje.periode.fraOgMed,
            clock = clock,
        ).getOrFail()

        val revurdering = GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
            id = gjenopptaId,
            opprettet = Tidspunkt.now(clock),
            oppdatert = Tidspunkt.now(clock),
            periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
            grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
            tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(sisteVedtakPåTidslinje.periode.fraOgMed)!!.id,
            vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
            saksbehandler = saksbehandler,
            simulering = simulerGjenopptak(
                sak = sak,
                gjenopptak = null,
                behandler = saksbehandler,
                clock = clock,
                utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
            ).getOrFail().simulering,
            revurderingsårsak = revurderingsårsak,
            sakinfo = sak.info(),
        )
        sak.nyRevurdering(revurdering) to revurdering
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
    clock: Clock = tikkendeFixedClock(),
    periode: Periode = Periode.create(
        fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
        tilOgMed = LocalDate.now(clock).plusMonths(11).endOfMonth(),
    ),
    sakOgVedtakSomKanRevurderes: Pair<Sak, VedtakSomKanRevurderes> = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
        periode = periode,
        clock = clock,
    ).let { it.first to it.second },
    attestering: Attestering = attesteringIverksatt(clock),
): Pair<Sak, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
    return simulertGjenopptakAvYtelseFraVedtakStansAvYtelse(
        periodeForStans = periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
    ).let { (sak, simulert) ->
        val iverksatt = simulert.iverksett(attestering)
            .getOrFail("Feil i oppsett for testdata")
        sak.oppdaterRevurdering(iverksatt) to iverksatt
    }
}

fun avsluttetGjenopptakelseAvYtelseeFraIverksattSøknadsbehandlignsvedtak(
    begrunnelse: String = "begrunnelse for å avslutte stans av ytelse",
    tidspunktAvsluttet: Tidspunkt = Tidspunkt.now(fixedClock),
    avsluttetAv: NavIdentBruker = saksbehandler,
): Pair<Sak, GjenopptaYtelseRevurdering.AvsluttetGjenoppta> {
    return simulertGjenopptakAvYtelseFraVedtakStansAvYtelse().let { (sak, simulert) ->
        val avsluttet = simulert.avslutt(
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
            avsluttetAv = avsluttetAv,
        ).getOrFail()

        sak.oppdaterRevurdering(avsluttet) to avsluttet
    }
}
