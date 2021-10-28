package no.nav.su.se.bakover.domain.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

internal class VurderOpphørVedRevurderingTest {

    private val fixedClock: Clock = Clock.fixed(1.juni(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `vilkår som ikke er oppfylt gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Avslag
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.UFØRHET)
            on { tidligsteDatoForAvslag() } doReturn 1.juni(2021)
        }
        val beregning = lagBeregningMedFradrag()

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.UFØRHET), 1.juni(2021))
    }

    @Test
    fun `en fremtidig måned under minstebeløp gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }

        val dato = 1.desember(2021)
        val beregning = lagBeregningMedFradrag(lagFradrag(beløpUnderMinstebeløp(dato), dato))

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), 1.desember(2021))
    }

    @Test
    fun `en fremtidig måned under minstebeløp pågrunn av sosialstønad gir ikke opphør `() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }

        val dato = 1.desember(2021)
        val beregning = lagBeregningMedFradrag(lagFradrag(beløpUnderMinstebeløp(dato), dato, Fradragstype.Sosialstønad))

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Nei
    }

    @Test
    fun `en fremtidig måned under minstebeløp pågrunn av sosialstønad fra ektefelle gir ikke opphør `() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }

        val dato = 1.desember(2021)
        val beregning = lagBeregningMedFradrag(
            lagFradrag(beløpUnderMinstebeløp(dato), dato, Fradragstype.Sosialstønad, FradragTilhører.EPS),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Nei
    }

    @Test
    fun `alle fremtidige måneder under minstebeløp gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }

        val beregning = lagBeregningMedFradrag(
            lagFradrag(beløpUnderMinstebeløp(1.november(2021)), 1.november(2021)),
            lagFradrag(1000.0, 1.desember(2021)),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), 1.november(2021))
    }

    @Test
    fun `inneværende måned under minstebeløp gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val now = LocalDate.now(fixedClock)
        val beregning = lagBeregningMedFradrag(lagFradrag(beløpUnderMinstebeløp(now), now))

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(
            listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE),
            LocalDate.now(fixedClock).startOfMonth(),
        )
    }

    @Test
    fun `en fremtidig måned med beløp lik 0 gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val beregning = lagBeregningMedFradrag(lagFradrag(100000.0, 1.desember(2021)))

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.FOR_HØY_INNTEKT), 1.desember(2021))
    }

    @Test
    fun `en fremtidig måned med beløp lik 0 fra sosialstønad gir ikke opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val beregning = lagBeregningMedFradrag(lagFradrag(100000.0, 1.desember(2021), Fradragstype.Sosialstønad))

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Nei
    }

    @Test
    fun `en fremtidig måned med beløp lik 0 fra EPS sosialstønad gir ikke opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val beregning = lagBeregningMedFradrag(
            lagFradrag(
                100000.0,
                1.desember(2021),
                Fradragstype.Sosialstønad,
                FradragTilhører.EPS,
            ),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Nei
    }

    @Test
    fun `en måned i fortiden under minstebeløp gir ikke opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val beregning = lagBeregningMedFradrag(
            lagFradrag(beløpUnderMinstebeløp(1.januar(2021)), 1.januar(2021)),
            lagFradrag(1.0, 1.februar(2021)),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Nei
    }

    @Test
    fun `en måned i fortiden med beløp lik 0 gir ikke opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val beregning = lagBeregningMedFradrag(
            lagFradrag(1000000.0, 1.januar(2021)),
            lagFradrag(1.0, 1.februar(2021)),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Nei
    }

    @Test
    fun `alle måneder i fortiden med beløp lik 0 gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val beregning = lagBeregningMedFradrag(
            lagFradrag(1000000.0, 1.januar(2021)),
            lagFradrag(1000000.0, 1.februar(2021)),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.FOR_HØY_INNTEKT), 1.januar(2021))
    }

    @Test
    fun `alle måneder i fortiden med beløp under minstegrense gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val beregning = lagBeregningMedFradrag(
            lagFradrag(beløpUnderMinstebeløp(1.januar(2021)), 1.januar(2021)),
            lagFradrag(beløpUnderMinstebeløp(1.februar(2021)), 1.februar(2021)),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), 1.januar(2021))
    }

    @Test
    fun `kaster exception hvis vilkårsvurderinger svarer med uavklart`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Uavklart
        }
        assertThrows<IllegalStateException> {
            VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                vilkårsvurderinger = vilkårsvurderingerMock,
                beregning = mock(),
                clock = fixedClock,
            )
        }
    }

    @Test
    fun `vilkår har prioritet over beregning når det kommer til hva som fører til opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Avslag
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.UFØRHET)
            on { tidligsteDatoForAvslag() } doReturn LocalDate.EPOCH
        }
        val beregning = lagBeregningMedFradrag(
            lagFradrag(1000000.0, 1.januar(2021)),
            lagFradrag(1000000.0, 1.februar(2021)),
        )

        VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.UFØRHET), LocalDate.EPOCH)
    }

    private fun lagBeregningMedFradrag(vararg fradrag: Fradrag): Beregning {
        val periode = if (fradrag.isEmpty()) periode2021 else Periode.create(
            fradrag.minOf { it.periode.fraOgMed },
            fradrag.maxOf { it.periode.tilOgMed },
        )
        return BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                *fradrag,
                FradragFactory.ny(
                    Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            begrunnelse = null,
        )
    }

    private fun lagFradrag(
        beløp: Double,
        localDate: LocalDate,
        fradragstype: Fradragstype? = null,
        tilhører: FradragTilhører? = null,
    ) = FradragFactory.ny(
        type = fradragstype ?: Fradragstype.Kapitalinntekt,
        månedsbeløp = beløp,
        periode = Periode.create(localDate.startOfMonth(), localDate.endOfMonth()),
        utenlandskInntekt = null,
        tilhører = tilhører ?: FradragTilhører.BRUKER,
    )

    private fun beløpUnderMinstebeløp(dato: LocalDate): Double {
        val minstebeløp = Sats.toProsentAvHøy(månedsperiode(dato))
        return Sats.HØY.månedsbeløp(dato) - minstebeløp + 1
    }

    private fun månedsperiode(localDate: LocalDate) = Periode.create(localDate.startOfMonth(), localDate.endOfMonth())
}
