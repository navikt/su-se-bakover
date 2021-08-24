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
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
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
        val beregningMock = mock<Beregning>()

        VurderOpphørVedRevurdering(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregningMock,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.UFØRHET), 1.juni(2021))
    }

    @Test
    fun `en fremtidig måned under minstebeløp gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val månedsberegningMock = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val beregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock)
        }

        VurderOpphørVedRevurdering(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregningMock,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), 1.desember(2021))
    }

    @Test
    fun `alle fremtidige måneder under minstebeløp gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val månedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val månedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val beregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock1, månedsberegningMock2)
        }

        VurderOpphørVedRevurdering(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregningMock,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), 1.november(2021))
    }

    @Test
    fun `inneværende måned under minstebeløp gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val månedsberegningMock = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(LocalDate.now(fixedClock).startOfMonth(), LocalDate.now(fixedClock).endOfMonth())
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val beregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock)
        }

        VurderOpphørVedRevurdering(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregningMock,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), LocalDate.now(fixedClock).startOfMonth())
    }

    @Test
    fun `en fremtidig måned med beløp lik 0 gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val månedsberegningMock = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn false
            on { getSumYtelse() } doReturn 0
        }
        val beregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock)
        }

        VurderOpphørVedRevurdering(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregningMock,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.FOR_HØY_INNTEKT), 1.desember(2021))
    }

    @Test
    fun `en måned i fortiden under minstebeløp gir ikke opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val månedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.januar(2021), 31.januar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val månedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.februar(2021), 28.februar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn false
            on { getSumYtelse() } doReturn 5000
        }
        val beregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock1, månedsberegningMock2)
        }

        VurderOpphørVedRevurdering(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregningMock,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Nei
    }

    @Test
    fun `en måned i fortiden med beløp lik 0 gir ikke opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val månedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.januar(2021), 31.januar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn false
            on { getSumYtelse() } doReturn 0
        }
        val månedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.februar(2021), 28.februar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn false
            on { getSumYtelse() } doReturn 5000
        }
        val beregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock1, månedsberegningMock2)
        }

        VurderOpphørVedRevurdering(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregningMock,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Nei
    }

    @Test
    fun `alle måneder i fortiden med beløp lik 0 gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val månedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.januar(2021), 31.januar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn false
            on { getSumYtelse() } doReturn 0
        }
        val månedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.februar(2021), 28.februar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn false
            on { getSumYtelse() } doReturn 0
        }
        val beregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock1, månedsberegningMock2)
            on { alleMånederErUnderMinstebeløp() } doReturn false
            on { alleMånederHarBeløpLik0() } doReturn true
        }

        VurderOpphørVedRevurdering(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregningMock,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.FOR_HØY_INNTEKT), 1.januar(2021))
    }

    @Test
    fun `alle måneder i fortiden med beløp under minstegrense gir opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val månedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.januar(2021), 31.januar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn true
            on { getSumYtelse() } doReturn 0
        }
        val månedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.februar(2021), 28.februar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn true
            on { getSumYtelse() } doReturn 0
        }
        val beregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock1, månedsberegningMock2)
            on { alleMånederErUnderMinstebeløp() } doReturn true
            on { alleMånederHarBeløpLik0() } doReturn true
        }

        VurderOpphørVedRevurdering(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregningMock,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), 1.januar(2021))
    }

    @Test
    fun `kaster exception hvis vilkårsvurderinger svarer med uavklart`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Uavklart
        }
        assertThrows<IllegalStateException> {
            VurderOpphørVedRevurdering(
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
        val månedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.januar(2021), 31.januar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn true
            on { getSumYtelse() } doReturn 0
        }
        val månedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.februar(2021), 28.februar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn true
            on { getSumYtelse() } doReturn 0
        }
        val beregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock1, månedsberegningMock2)
            on { alleMånederErUnderMinstebeløp() } doReturn true
            on { alleMånederHarBeløpLik0() } doReturn true
        }

        VurderOpphørVedRevurdering(
            vilkårsvurderinger = vilkårsvurderingerMock,
            beregning = beregningMock,
            clock = fixedClock,
        ).resultat shouldBe OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.UFØRHET), LocalDate.EPOCH)
    }
}
