package no.nav.su.se.bakover.domain.revurdering

import arrow.core.nonEmptyListOf
import behandling.revurdering.domain.Opphørsgrunn
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.november
import no.nav.su.se.bakover.common.domain.tid.startOfDay
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOpphørVedRevurdering
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
import vilkår.common.domain.Vurdering
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.VurderingsperiodeUføre
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class VurderOpphørVedRevurderingTest {

    private val fixedClock: Clock = Clock.fixed(1.juni(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `vilkår som ikke er oppfylt gir opphør`() {
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(
            uføre = UføreVilkår.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUføre.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Innvilget,
                        grunnlag = null,
                        periode = Periode.create(1.januar(2021), 31.mai(2021)),
                    ),
                    VurderingsperiodeUføre.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Avslag,
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
