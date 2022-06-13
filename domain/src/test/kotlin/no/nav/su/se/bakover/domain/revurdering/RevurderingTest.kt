package no.nav.su.se.bakover.domain.revurdering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.harAlleMånederMerknadForAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class RevurderingTest {

    @Test
    fun `beregning gir opphør hvis vilkår ikke er oppfylt`() {
        opprettetRevurdering(
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(),
            ),
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
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.UFØRHET)
            }
        }
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `beregning gir ikke opphør hvis vilkår er oppfylt`() {
        opprettetRevurdering().let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let {
                it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
            }
        }
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `beregningen gir ikke opphør dersom beløpet er under minstegrense, men endringen er mindre enn 10 prosent`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
            grunnlagsdata = grunnlagsdataEnsligMedFradrag(
                fradragsgrunnlag = nonEmptyListOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                        arbeidsinntekt = satsFactoryTestPåDato().høyUføre(januar(2020)).satsForMånedAsDouble - 440.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    fradragsgrunnlagArbeidsinntekt(
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                        arbeidsinntekt = satsFactoryTestPåDato().høyUføre(mai(2021)).satsForMånedAsDouble - 440.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )

        opprettetRevurdering(
            sakOgVedtakSomKanRevurderes = sak to vedtak,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = Periode.create(1.januar(2020), 30.april(2020)),
                    arbeidsinntekt = 20535.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
                fradragsgrunnlagArbeidsinntekt(
                    periode = Periode.create(1.mai(2020), 31.desember(2020)),
                    arbeidsinntekt = 21735.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
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
                it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
                it.beregning.harAlleMånederMerknadForAvslag() shouldBe true
            }
        }
    }

    @Test
    fun `beregning med beløpsendring større enn 10 prosent fører til endring`() {
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 9000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
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
                it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
            }
        }
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `beregning med beløpsendring mindre enn 10 prosent fører ikke til endring`() {
        opprettetRevurdering().let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let {
                it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
            }
        }
    }

    @Test
    fun `beregning med beløpsendring mindre enn 10 prosent fører til endring - g regulering`() {
        opprettetRevurdering(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(),
                begrunnelse = "regger",
            ),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 25.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
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
                it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
            }
        }
    }

    @Test
    fun `beregning uten beløpsendring fører til ingen endring - g regulering`() {
        opprettetRevurdering(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(),
                begrunnelse = "regger",
            ),
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
                it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
            }
        }
    }

    @Test
    fun `beregning som fører til beløp lik 0 gir opphør - g regulering`() {
        opprettetRevurdering(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(),
                begrunnelse = "regger",
            ),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 350_000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
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
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            }
        }
    }

    @Test
    fun `beregning som fører til beløp lik 0 gir opphør`() {
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 350_000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
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
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            }
        }
    }

    @Test
    fun `beregning som fører til beløp under minstegrense gir opphør`() {
        val periode = år(2021)
        opprettetRevurdering(
            stønadsperiode = Stønadsperiode.create(periode),
            revurderingsperiode = periode,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                    arbeidsinntekt = 20800.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
                fradragsgrunnlagArbeidsinntekt(
                    periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    arbeidsinntekt = 21800.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
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
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
            }
        }
    }
}
