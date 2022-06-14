package no.nav.su.se.bakover.domain.revurdering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.beregningAvslagForHøyInntekt
import no.nav.su.se.bakover.test.beregningAvslagUnderMinstebeløp
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderingerRevurderingInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class VurderOpphørVedRevurderingTest {

    private val fixedClock: Clock = Clock.fixed(1.juni(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `vilkår som ikke er oppfylt gir opphør`() {
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(
            uføre = Vilkår.Uførhet.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    Vurderingsperiode.Uføre.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = null,
                        periode = Periode.create(1.januar(2021), 31.mai(2021)),
                    ),
                    Vurderingsperiode.Uføre.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Avslag,
                        grunnlag = null,
                        periode = Periode.create(1.juni(2021), 31.desember(2021)),
                    ),
                ),
            ),
        )
        val beregning = beregning()

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderinger,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.UFØRHET), 1.juni(2021))
    }

    @Test
    fun `en fremtidig måned under minstebeløp gir opphør`() {
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget()
        val beregning = beregning(
            fradragsgrunnlag = nonEmptyListOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = november(2021),
                    arbeidsinntekt = satsFactoryTestPåDato().høyUføre(november(2021)).satsForMånedAsDouble - 100.0,
                ),
            ),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderinger,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), 1.november(2021))
    }

    @Test
    fun `alle fremtidige måneder under minstebeløp gir opphør`() {
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget()
        val beregning = beregning(
            fradragsgrunnlag = nonEmptyListOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = Periode.create(1.juli(2021), 31.desember(2021)),
                    arbeidsinntekt = satsFactoryTestPåDato().høyUføre(juli(2021)).satsForMånedAsDouble - 100.0,
                ),
            ),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderinger,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), 1.juli(2021))
    }

    @Test
    fun `inneværende måned under minstebeløp gir opphør`() {
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget()
        val beregning = beregning(
            fradragsgrunnlag = nonEmptyListOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = Periode.create(1.juni(2021), 31.desember(2021)),
                    arbeidsinntekt = satsFactoryTestPåDato().høyUføre(juni(2021)).satsForMånedAsDouble - 100.0,
                ),
            ),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderinger,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(
            listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE),
            1.juni(2021),
        )
    }

    @Test
    fun `en fremtidig måned med beløp lik 0 gir opphør`() {
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget()
        val beregning = beregning(
            fradragsgrunnlag = nonEmptyListOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = desember(2021),
                    arbeidsinntekt = 350_000.0,
                ),
            ),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderinger,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.FOR_HØY_INNTEKT), 1.desember(2021))
    }

    @Test
    fun `en måned i fortiden under minstebeløp gir ikke opphør`() {
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget()
        val beregning = beregning(
            fradragsgrunnlag = nonEmptyListOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mars(2021),
                    arbeidsinntekt = 20750.0,
                ),
            ),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderinger,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Nei
    }

    @Test
    fun `en måned i fortiden med beløp lik 0 gir ikke opphør`() {
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget()
        val beregning = beregning(
            fradragsgrunnlag = nonEmptyListOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mars(2021),
                    arbeidsinntekt = 250000.0,
                ),
            ),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderinger,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Nei
    }

    @Test
    fun `alle måneder i fortiden med beløp lik 0 gir opphør`() {
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget()
        val beregning = beregningAvslagForHøyInntekt()

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderinger,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.FOR_HØY_INNTEKT), 1.januar(2021))
    }

    @Test
    fun `alle måneder i fortiden med beløp under minstegrense gir opphør`() {
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget()
        val beregning = beregningAvslagUnderMinstebeløp()

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderinger,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), 1.januar(2021))
    }

    @Test
    fun `kaster exception hvis vilkårsvurderinger svarer med uavklart`() {
        assertThrows<IllegalStateException> {
            VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                beregning = mock(),
                clock = fixedClock,
            )
        }
    }

    @Test
    fun `vilkår har prioritet over beregning når det kommer til hva som fører til opphør`() {
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(
            uføre = avslåttUførevilkårUtenGrunnlag(),
        )
        val beregning = beregningAvslagForHøyInntekt()

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderinger,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.UFØRHET), 1.januar(2021))
    }
}
