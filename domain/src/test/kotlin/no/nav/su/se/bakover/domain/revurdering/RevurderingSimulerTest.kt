package no.nav.su.se.bakover.domain.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurderingAvslagSpesifiktVilkår
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simuleringNy
import no.nav.su.se.bakover.test.simulertUtbetalingOpphør
import no.nav.su.se.bakover.test.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail

class RevurderingSimulerTest {
    @Test
    fun `avkortingsvarsel dersom opphør skyldes utenlandsopphold og simulering inneholder feilutbetaling`() {
        opprettetRevurderingAvslagSpesifiktVilkår(
            avslåttVilkår = utenlandsoppholdAvslag(),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let { beregnet ->
                (beregnet as BeregnetRevurdering.Opphørt)
                    .toSimulert(
                        { sakId, _, opphørsdato ->
                            simulertUtbetalingOpphør(
                                sakId = sakId,
                                periode = beregnet.periode,
                                opphørsdato = opphørsdato,
                                eksisterendeUtbetalinger = sak.utbetalinger,
                            )
                        },
                        false
                    ).getOrFail()
                    .let { simulert ->
                        simulert.avkorting.let {
                            (it as AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel).let { avkorting ->
                                avkorting.avkortingsvarsel shouldBe Avkortingsvarsel.Utenlandsopphold.Opprettet(
                                    id = avkorting.avkortingsvarsel.id,
                                    sakId = revurdering.sakId,
                                    revurderingId = revurdering.id,
                                    opprettet = avkorting.avkortingsvarsel.opprettet,
                                    simulering = avkorting.avkortingsvarsel.simulering,
                                ).skalAvkortes()
                            }
                        }
                    }
            }
        }
    }

    @Test
    fun `kaster exception dersom simulering med justert opphørsdato for utbetaling inneholder feilutbetalinger`() {
        assertThrows<IllegalStateException> {
            opprettetRevurderingAvslagSpesifiktVilkår(
                avslåttVilkår = utenlandsoppholdAvslag(),
            ).let { (sak, revurdering) ->
                revurdering.beregn(
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = fixedClock,
                    gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                        fraOgMed = revurdering.periode.fraOgMed,
                        clock = fixedClock,
                    ).getOrFail(),
                    satsFactory = satsFactoryTestPåDato(),
                ).getOrFail().let { beregnet ->
                    (beregnet as BeregnetRevurdering.Opphørt)
                        .toSimulert(
                            { sakId, _, _ ->
                                simulertUtbetalingOpphør(
                                    sakId = sakId,
                                    periode = beregnet.periode,
                                    opphørsdato = beregnet.periode.fraOgMed,
                                    eksisterendeUtbetalinger = sak.utbetalinger,
                                )
                            },
                            false
                        ).getOrFail()
                }
            }
        }
    }

    @Test
    fun `ingen avkortingsvarsel dersom opphør ikke skyldes utenlandsopphold og simulering inneholder feilutbetaling`() {
        opprettetRevurderingAvslagSpesifiktVilkår(
            avslåttVilkår = avslåttUførevilkårUtenGrunnlag(),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let {
                (it as BeregnetRevurdering.Opphørt)
                    .toSimulert(
                        { sakId, _, opphørsdato ->
                            simulertUtbetalingOpphør(
                                sakId = sakId,
                                periode = it.periode,
                                opphørsdato = opphørsdato,
                                eksisterendeUtbetalinger = sak.utbetalinger,
                            )
                        },
                        false
                    ).getOrFail()
                    .let {
                        it.avkorting shouldBe AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
                    }
            }
        }
    }

    @Test
    fun `ingen avkortingsvarsel dersom opphør ikke fører til feilutbetaling`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsperiode = Periode.create(1.september(2021), 31.desember(2021)),
        ).let { (sak, revurdering) ->
            revurdering.oppdaterFradragOgMarkerSomVurdert(
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = Periode.create(1.september(2021), 31.desember(2021)),
                        arbeidsinntekt = 35000.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ).getOrFail().let {
                it.beregn(
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = fixedClock,
                    gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                        fraOgMed = revurdering.periode.fraOgMed,
                        clock = fixedClock,
                    ).getOrFail(),
                    satsFactory = satsFactoryTestPåDato(),
                ).getOrFail().let {
                    (it as BeregnetRevurdering.Opphørt)
                        .toSimulert(
                            { sakId, _, opphørsdato ->
                                simulertUtbetalingOpphør(
                                    sakId = sakId,
                                    periode = it.periode,
                                    opphørsdato = opphørsdato,
                                    eksisterendeUtbetalinger = sak.utbetalinger,
                                )
                            },
                            false
                        ).getOrFail()
                        .let {
                            it.avkorting shouldBe AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
                        }
                }
            }
        }
    }

    @Test
    fun `oppretter tilbakekrevingsbehandling dersom simulering inneholder feilutbetaling som ikke skyldes utlandsopphold`() {
        beregnetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mars(2021),
                    arbeidsinntekt = 15799.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (sak, beregnet) ->
            (beregnet as BeregnetRevurdering.Innvilget).let {
                when (
                    it.toSimulert(
                        simulering = simuleringNy(
                            beregning = it.beregning,
                            eksisterendeUtbetalinger = sak.utbetalinger,
                        ),
                        clock = fixedClock,
                        tilbakekrevingTillatt = true,
                    ).tilbakekrevingsbehandling
                ) {
                    is IkkeAvgjort -> {}
                    else -> fail("Skulle opprettet tilbakekrevingsbehandling")
                }
            }
        }
    }
}
