package no.nav.su.se.bakover.domain.revurdering

import arrow.core.NonEmptyList
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.harAlleMånederMerknadForAvslag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurderingAvslagUføre
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.UUID
import kotlin.math.abs

internal class RevurderingBeregnTest {

    @Test
    fun `beregning gir opphør hvis vilkår ikke er oppfylt`() {
        opprettetRevurderingAvslagUføre().let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.UFØRHET)
            }
        }
    }

    @Test
    fun `beregning gir ikke opphør hvis vilkår er oppfylt`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().let { (sak, revurdering) ->
            revurdering
                .beregn(
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = fixedClock,
                ).getOrFail().let {
                    over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                    it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
                }
        }
    }

    @Test
    fun `beregningen gir ikke opphør dersom beløpet er under minstegrense, men endringen er mindre enn 10 prosent`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().let { (_, revurdering) ->
            revurdering
                .oppdaterFradragOgMarkerSomVurdert(
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            periode = Periode.create(1.januar(2021), 30.april(2021)),
                            arbeidsinntekt = 20535.0,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        fradragsgrunnlagArbeidsinntekt(
                            periode = Periode.create(1.mai(2021), 31.desember(2021)),
                            arbeidsinntekt = 21735.0,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                )
                .getOrFail()
                .beregn(
                    eksisterendeUtbetalinger = listOf(lagUtbetaling(lagUtbetalingslinje(440, revurdering.periode))),
                    clock = fixedClock,
                ).getOrFail().let {
                    it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
                    it.beregning.harAlleMånederMerknadForAvslag() shouldBe true
                }
        }
    }

    @Test
    fun `beregning med beløpsendring større enn 10 prosent fører til endring`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().let { (sak, revurdering) ->
            revurdering
                .beregn(
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = fixedClock,
                ).getOrFail().let {
                    over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                    it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
                }
        }
    }

    @Test
    fun `beregning med beløpsendring mindre enn 10 prosent fører ikke til endring`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().let { (sak, revurdering) ->
            revurdering
                .oppdaterFradragOgMarkerSomVurdert(
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            periode = revurdering.periode,
                            arbeidsinntekt = 6000.0,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                )
                .getOrFail()
                .beregn(
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = fixedClock,
                ).getOrFail().let {
                    over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe false
                    it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
                }
        }
    }

    @Test
    fun `beregning med beløpsendring mindre enn 10 prosent fører til endring - g regulering`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(), begrunnelse = "a",
            ),
        ).let { (sak, revurdering) ->
            revurdering
                .oppdaterFradragOgMarkerSomVurdert(
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            periode = revurdering.periode,
                            arbeidsinntekt = 6000.0,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                )
                .getOrFail()
                .beregn(
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = fixedClock,
                ).getOrFail().let {
                    over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe false
                    it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
                }
        }
    }

    @Test
    fun `beregning uten beløpsendring fører til ingen endring - g regulering`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(), begrunnelse = "a",
            ),
        ).let { (sak, revurdering) ->
            revurdering
                .oppdaterFradragOgMarkerSomVurdert(
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            periode = Periode.create(1.januar(2021), 30.april(2021)),
                            arbeidsinntekt = (
                                Sats.HØY.månedsbeløp(1.januar(2021)) - sak.utbetalinger.flatMap { it.utbetalingslinjer }
                                    .sumOf { it.beløp }
                                ),
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        fradragsgrunnlagArbeidsinntekt(
                            periode = Periode.create(1.mai(2021), 31.desember(2021)),
                            arbeidsinntekt = (
                                Sats.HØY.månedsbeløp(1.mai(2021)) - sak.utbetalinger.flatMap { it.utbetalingslinjer }
                                    .sumOf { it.beløp }
                                ),
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                )
                .getOrFail()
                .beregn(
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = fixedClock,
                ).getOrFail().let {
                    over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe false
                    it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
                }
        }
    }

    @Test
    fun `beregning som fører til beløp lik 0 gir opphør - g regulering`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(), begrunnelse = "a",
            ),
        ).let { (sak, revurdering) ->
            revurdering
                .oppdaterFradragOgMarkerSomVurdert(
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            periode = revurdering.periode,
                            arbeidsinntekt = 350_000.0,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                )
                .getOrFail()
                .beregn(
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = fixedClock,
                ).getOrFail().let {
                    over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                    it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                    (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
                }
        }
    }

    @Test
    fun `beregning som fører til beløp lik 0 gir opphør`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().let { (sak, revurdering) ->
            revurdering
                .oppdaterFradragOgMarkerSomVurdert(
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            periode = revurdering.periode,
                            arbeidsinntekt = 350_000.0,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                )
                .getOrFail()
                .beregn(
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = fixedClock,
                ).getOrFail().let {
                    over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                    it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                    (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
                }
        }
    }

    @Test
    fun `beregning som fører til beløp under minstegrense gir opphør`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().let { (sak, revurdering) ->
            revurdering
                .oppdaterFradragOgMarkerSomVurdert(
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            periode = Periode.create(1.januar(2021), 30.april(2021)),
                            arbeidsinntekt = (Sats.HØY.månedsbeløp(1.januar(2021)) - 250),
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        fradragsgrunnlagArbeidsinntekt(
                            periode = Periode.create(1.mai(2021), 31.desember(2021)),
                            arbeidsinntekt = (Sats.HØY.månedsbeløp(1.mai(2021)) - 250),
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                )
                .getOrFail()
                .beregn(
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = fixedClock,
                ).getOrFail().let {
                    over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                    it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                    (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
                }
        }
    }

    private fun lagUtbetaling(
        vararg utbetalingslinjer: Utbetalingslinje,
    ) = Utbetaling.OversendtUtbetaling.MedKvittering(
        opprettet = fixedTidspunkt,
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(9999),
        fnr = Fnr.generer(),
        utbetalingslinjer = NonEmptyList.fromListUnsafe(utbetalingslinjer.toList()),
        type = Utbetaling.UtbetalingsType.NY,
        behandler = mock(),
        avstemmingsnøkkel = mock(),
        simulering = mock(),
        utbetalingsrequest = mock(),
        kvittering = mock(),
    )

    private fun lagUtbetalingslinje(månedsbeløp: Int, periode: Periode) = Utbetalingslinje.Ny(
        opprettet = fixedTidspunkt,
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = null,
        beløp = månedsbeløp,
        uføregrad = Uføregrad.parse(50),
    )

    private fun over10ProsentEndring(
        nyBeregning: Beregning,
        eksisterendeUtbetalinger: List<Utbetaling>,
    ): Boolean {
        val nyBeregningBeløp = nyBeregning.getSumYtelse()
        val eksisterendeBeløp = eksisterendeUtbetalinger.sumOf {
            TidslinjeForUtbetalinger(
                periode = nyBeregning.periode,
                utbetalingslinjer = eksisterendeUtbetalinger.flatMap { it.utbetalingslinjer },
                clock = fixedClock,
            ).tidslinje.sumOf { it.beløp * it.periode.getAntallMåneder() }
        }
        return abs((nyBeregningBeløp.toDouble() - eksisterendeBeløp) / eksisterendeBeløp * 100) > 10.0
    }
}
