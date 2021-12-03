package no.nav.su.se.bakover.domain.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Feilutbetalingsvarsel
import no.nav.su.se.bakover.test.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurderingAvslagSpesifiktVilkår
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertUtbetalingOpphør
import no.nav.su.se.bakover.test.utlandsoppholdAvslag
import org.junit.jupiter.api.Test

class RevurderingSimulerTest {
    @Test
    fun `feilutbetalingsvarsel for avkorting dersom opphør skyldes utenlandsopphold og simulering inneholder feilutbetaling`() {
        opprettetRevurderingAvslagSpesifiktVilkår(
            avslåttVilkår = utlandsoppholdAvslag(),
        ).let { (sak, revurdering) ->
            revurdering.beregn(sak.utbetalinger, fixedClock)
                .getOrFail().let {
                    (it as BeregnetRevurdering.Opphørt)
                        .toSimulert(
                            simulertUtbetalingOpphør(
                                periode = it.periode,
                                opphørsdato = it.periode.fraOgMed,
                                eksisterendeUtbetalinger = sak.utbetalinger,
                            ),
                        ).let {
                            it.feilutbetalingsvarsel.let {
                                (it as Feilutbetalingsvarsel.KanAvkortes).let {
                                    it shouldBe Feilutbetalingsvarsel.KanAvkortes(
                                        id = it.id,
                                        opprettet = it.opprettet,
                                        simulering = it.simulering,
                                        feilutbetalingslinje = Feilutbetalingsvarsel.Feilutbetalingslinje(
                                            fraOgMed = revurdering.periode.fraOgMed,
                                            tilOgMed = revurdering.periode.tilOgMed,
                                            virkningstidspunkt = revurdering.periode.fraOgMed,
                                            forrigeUtbetalingslinjeId = null,
                                            beløp = 15000,
                                            uføregrad = Uføregrad.parse(50),
                                        ),
                                    )
                                }
                            }
                        }
                }
        }
    }

    @Test
    fun `feilutbetalingsvarsel må tilbakekreves dersom opphør ikke skyldes utenlandsopphold og simulering inneholder feilutbetaling`() {
        opprettetRevurderingAvslagSpesifiktVilkår(
            avslåttVilkår = avslåttUførevilkårUtenGrunnlag(),
        ).let { (sak, revurdering) ->
            revurdering.beregn(sak.utbetalinger, fixedClock)
                .getOrFail().let {
                    (it as BeregnetRevurdering.Opphørt)
                        .toSimulert(
                            simulertUtbetalingOpphør(
                                periode = it.periode,
                                opphørsdato = it.periode.fraOgMed,
                                eksisterendeUtbetalinger = sak.utbetalinger,
                            ),
                        ).let {
                            it.feilutbetalingsvarsel shouldBe Feilutbetalingsvarsel.MåTilbakekreves
                        }
                }
        }
    }

    @Test
    fun `ingen feilutbetalingsvarsel dersom opphør ikke fører til feilutbetaling`() {
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
                it.beregn(sak.utbetalinger, fixedClock)
                    .getOrFail().let {
                        (it as BeregnetRevurdering.Opphørt)
                            .toSimulert(
                                simulertUtbetalingOpphør(
                                    periode = it.periode,
                                    opphørsdato = it.periode.fraOgMed,
                                    eksisterendeUtbetalinger = sak.utbetalinger,
                                ),
                            ).let {
                                it.feilutbetalingsvarsel shouldBe Feilutbetalingsvarsel.Ingen
                            }
                    }
            }
        }
    }
}
