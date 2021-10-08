package no.nav.su.se.bakover.domain.revurdering

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Endring
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.endringFra
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.test.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.avstemmingsnøkkel
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.test.kvittering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeEtterGeregulering2021
import no.nav.su.se.bakover.test.periodeFørGeregulering2021
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simuleringNy
import no.nav.su.se.bakover.test.utbetalingsRequest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.UUID

internal class RevurderingTest {

    @Test
    fun `beregning gir opphør hvis vilkår ikke er oppfylt`() {
        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger(
                periode = periode2021,
                uføreVilkår = avslåttUførevilkårUtenGrunnlag(),
            ),
        )

        revurdering.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            utgangspunkt = sak.hentGjeldendeMånedsberegningForEnkeltmåned(revurdering.periode.månedenFør())
                .getOrElse { null },
        ).getOrFail()
            .let {
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.UFØRHET)
            }
    }

    @Disabled("dette caset er nok ikke mulig etter berenging med virkningstidspunkt")
    fun `beregningen gir ikke opphør dersom beløpet er under minstegrense, men endringen er mindre enn 10 prosent`() {
        // val periode = Periode.create(1.januar(2021), 31.desember(2021))
        // lagRevurdering(
        //     periode = periode,
        //     vilkårsvurderinger = Vilkårsvurderinger(
        //         uføre = Vilkår.Uførhet.Vurdert.create(
        //             vurderingsperioder = nonEmptyListOf(
        //                 Vurderingsperiode.Uføre.create(
        //                     id = UUID.randomUUID(),
        //                     opprettet = Tidspunkt.now(),
        //                     resultat = Resultat.Innvilget,
        //                     grunnlag = null,
        //                     periode = periode,
        //                     begrunnelse = null,
        //                 ),
        //             ),
        //         ),
        //         formue = innvilgetFormueVilkår(periode),
        //     ),
        //     bosituasjon = listOf(
        //         Grunnlag.Bosituasjon.Fullstendig.Enslig(
        //             id = UUID.randomUUID(),
        //             opprettet = fixedTidspunkt,
        //             periode = periode,
        //             begrunnelse = null,
        //         ),
        //     ),
        //     fradrag = listOf(
        //         lagFradragsgrunnlag(
        //             type = Fradragstype.Arbeidsinntekt,
        //             månedsbeløp = 20535.0,
        //             periode = Periode.create(periode.fraOgMed, 30.april(2021)),
        //             tilhører = FradragTilhører.BRUKER,
        //         ),
        //         lagFradragsgrunnlag(
        //             type = Fradragstype.Arbeidsinntekt,
        //             månedsbeløp = 21735.0,
        //             periode = Periode.create(1.mai(2021), periode.tilOgMed),
        //             tilhører = FradragTilhører.BRUKER,
        //         ),
        //     ),
        // ).beregn(
        //     eksisterendeUtbetalinger = listOf(
        //         lagUtbetaling(lagUtbetalingslinje(440, periode)),
        //     ),
        // ).orNull()!!.let {
        //     it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
        //     it.beregning.alleMånederErUnderMinstebeløp() shouldBe true
        // }
    }

    @Test
    fun `beregning med beløpsendring større enn 10 prosent i forhold til eksisterende utbetalinger fører til endring`() {
        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger(
                periode = periode2021,
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periode2021,
                        arbeidsinntekt = 10000.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )

        revurdering.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            utgangspunkt = sak.hentGjeldendeMånedsberegningForEnkeltmåned(revurdering.periode.månedenFør())
                .getOrElse { null },
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
        }
    }

    @Test
    fun `beregning med beløpsendring mindre enn 10 prosent i forhold til eksisterede utbetalinger fører ikke til endring`() {
        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak()

        revurdering.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            utgangspunkt = sak.hentGjeldendeMånedsberegningForEnkeltmåned(revurdering.periode.månedenFør())
                .getOrElse { null },
        ).getOrFail().let { beregnetRevurdering ->
            beregnetRevurdering shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
            beregnetRevurdering.beregning.getMånedsberegninger()
                .filter { it.periode.starterTidligere(periodeEtterGeregulering2021) }
                .all { it.getSumYtelse() == 20946 } shouldBe true
            beregnetRevurdering.beregning.getMånedsberegninger()
                .filter { it.periode.starterSamtidigEllerSenere(periodeEtterGeregulering2021) }
                .all { it.getSumYtelse() == 21989 } shouldBe true
        }
    }

    @Test
    fun `beregning med beløpsendring mindre enn 10 prosent i forhold til eksisterende utbetalinger fører til endring dersom utbetalingene ikke er g-regulert`() {
        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak()

        val uregulerteUtbetalinger = lagUtbetaling(
            lagUtbetalingslinje(
                månedsbeløp = Sats.HØY.månedsbeløpSomHeltall(revurdering.periode.fraOgMed),
                periode = revurdering.periode,
            ),
        )

        revurdering.beregn(
            eksisterendeUtbetalinger = listOf(uregulerteUtbetalinger),
            utgangspunkt = sak.hentGjeldendeMånedsberegningForEnkeltmåned(revurdering.periode.månedenFør())
                .getOrElse { null },
        ).getOrFail().let { beregnetRevurdering ->
            uregulerteUtbetalinger.utbetalingslinjer.all { it.beløp == 20946 } shouldBe true

            beregnetRevurdering shouldBe beOfType<BeregnetRevurdering.Innvilget>()
            beregnetRevurdering.beregning.getMånedsberegninger().all {
                it.getSumYtelse() endringFra uregulerteUtbetalinger.utbetalingslinjer[0].beløp == Endring.ENDRING_UNDER_10_PROSENT
            }
            beregnetRevurdering.beregning.getMånedsberegninger()
                .filter { it.periode.starterTidligere(periodeEtterGeregulering2021) }
                .all { it.getSumYtelse() == 20946 } shouldBe true
            beregnetRevurdering.beregning.getMånedsberegninger()
                .filter { it.periode.starterSamtidigEllerSenere(periodeEtterGeregulering2021) }
                .all { it.getSumYtelse() == 21989 } shouldBe true
        }
    }

    @Test
    fun `beregning som fører til beløp lik 0 gir opphør`() {
        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger(
                periode = periode2021,
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periode2021,
                        arbeidsinntekt = 500_000.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )
        revurdering.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            utgangspunkt = sak.hentGjeldendeMånedsberegningForEnkeltmåned(revurdering.periode.månedenFør())
                .getOrElse { null },
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
            (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
        }
    }

    @Test
    fun `beregning som fører til beløp under minstegrense gir opphør`() {
        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger(
                periode = periode2021,
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periodeFørGeregulering2021,
                        arbeidsinntekt = 20800.0,
                    ),
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periodeEtterGeregulering2021,
                        arbeidsinntekt = 21800.0,
                    ),
                ),
            ),
        )
        revurdering.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            utgangspunkt = sak.hentGjeldendeMånedsberegningForEnkeltmåned(revurdering.periode.månedenFør())
                .getOrElse { null },
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
            (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
        }
    }

    private fun lagUtbetaling(
        vararg utbetalingslinjer: Utbetalingslinje,
    ) = Utbetaling.OversendtUtbetaling.MedKvittering(
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(9999),
        fnr = Fnr.generer(),
        utbetalingslinjer = NonEmptyList.fromListUnsafe(utbetalingslinjer.toList()),
        type = Utbetaling.UtbetalingsType.NY,
        behandler = saksbehandler,
        avstemmingsnøkkel = avstemmingsnøkkel,
        simulering = simuleringNy(),
        utbetalingsrequest = utbetalingsRequest,
        kvittering = kvittering(),
    )

    private fun lagUtbetalingslinje(månedsbeløp: Int, periode: Periode) = Utbetalingslinje.Ny(
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = null,
        beløp = månedsbeløp,
        uføregrad = Uføregrad.parse(50),
    )
}
