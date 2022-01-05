package no.nav.su.se.bakover.domain.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.test.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurderingAvslagSpesifiktVilkår
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertUtbetalingOpphør
import no.nav.su.se.bakover.test.utlandsoppholdAvslag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RevurderingSimulerTest {
    @Test
    fun `avkortingsvarsel dersom opphør skyldes utenlandsopphold og simulering inneholder feilutbetaling`() {
        opprettetRevurderingAvslagSpesifiktVilkår(
            avslåttVilkår = utlandsoppholdAvslag(),
        ).let { (sak, revurdering) ->
            revurdering.beregn(sak.utbetalinger, fixedClock,)
                .getOrFail().let { beregnet ->
                    (beregnet as BeregnetRevurdering.Opphørt)
                        .toSimulert { sakId, _, opphørsdato ->
                            simulertUtbetalingOpphør(
                                sakId = sakId,
                                periode = beregnet.periode,
                                opphørsdato = opphørsdato,
                                eksisterendeUtbetalinger = sak.utbetalinger,
                            )
                        }.getOrFail()
                        .let { simulert ->
                            simulert.avkortingsvarsel.let {
                                (it as Avkortingsvarsel.Utenlandsopphold).let { avkortingsvarsel ->
                                    avkortingsvarsel shouldBe Avkortingsvarsel.Utenlandsopphold.Opprettet(
                                        id = avkortingsvarsel.id,
                                        sakId = revurdering.sakId,
                                        revurderingId = revurdering.id,
                                        opprettet = avkortingsvarsel.opprettet,
                                        simulering = avkortingsvarsel.simulering,
                                    )
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
                avslåttVilkår = utlandsoppholdAvslag(),
            ).let { (sak, revurdering) ->
                revurdering.beregn(sak.utbetalinger, fixedClock,)
                    .getOrFail().let { beregnet ->
                        (beregnet as BeregnetRevurdering.Opphørt)
                            .toSimulert { sakId, _, _ ->
                                simulertUtbetalingOpphør(
                                    sakId = sakId,
                                    periode = beregnet.periode,
                                    opphørsdato = beregnet.periode.fraOgMed,
                                    eksisterendeUtbetalinger = sak.utbetalinger,
                                )
                            }.getOrFail()
                    }
            }
        }
    }

    @Test
    fun `ingen avkortingsvarsel dersom opphør ikke skyldes utenlandsopphold og simulering inneholder feilutbetaling`() {
        opprettetRevurderingAvslagSpesifiktVilkår(
            avslåttVilkår = avslåttUførevilkårUtenGrunnlag(),
        ).let { (sak, revurdering) ->
            revurdering.beregn(sak.utbetalinger, fixedClock,)
                .getOrFail().let {
                    (it as BeregnetRevurdering.Opphørt)
                        .toSimulert { sakId, _, opphørsdato ->
                            simulertUtbetalingOpphør(
                                sakId = sakId,
                                periode = it.periode,
                                opphørsdato = opphørsdato,
                                eksisterendeUtbetalinger = sak.utbetalinger,
                            )
                        }.getOrFail()
                        .let {
                            it.avkortingsvarsel shouldBe Avkortingsvarsel.Ingen
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
                it.beregn(sak.utbetalinger, fixedClock,)
                    .getOrFail().let {
                        (it as BeregnetRevurdering.Opphørt)
                            .toSimulert { sakId, _, opphørsdato ->
                                simulertUtbetalingOpphør(
                                    sakId = sakId,
                                    periode = it.periode,
                                    opphørsdato = opphørsdato,
                                    eksisterendeUtbetalinger = sak.utbetalinger,
                                )
                            }.getOrFail()
                            .let {
                                it.avkortingsvarsel shouldBe Avkortingsvarsel.Ingen
                            }
                    }
            }
        }
    }
}
