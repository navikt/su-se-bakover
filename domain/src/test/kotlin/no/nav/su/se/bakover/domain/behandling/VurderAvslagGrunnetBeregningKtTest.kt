package no.nav.su.se.bakover.domain.behandling

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import org.junit.jupiter.api.Test

internal class VurderAvslagGrunnetBeregningKtTest {

    @Test
    fun `beregning som ikke eksisterer kan ikke gi avslag`() {
        vurderAvslagGrunnetBeregning(null) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `beregning med sum ytelse nøyaktig lik null skal gi avslag`() {
        val månedsberegning = mock<Månedsberegning> {
            on { erSumYtelseUnderMinstebeløp() } doReturn false
            on { getSumYtelse() } doReturn 0
        }
        val beregning = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegning)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
    }

    @Test
    fun `beregning med negativ sum ytelse skal gi avslag`() {
        val månedsberegning = mock<Månedsberegning> {
            on { erSumYtelseUnderMinstebeløp() } doReturn false
            on { getSumYtelse() } doReturn -4500
        }
        val beregning = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegning)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
    }

    @Test
    fun `beregning med alle måneder under minstebeløp skal gi avslag`() {
        val januar = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.januar(2021), 31.januar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val desember = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val beregning = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(januar, desember)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE,
        )
    }

    @Test
    fun `sjekker avslag for beløp under minstegrense før beløp lik 0`() {
        val januar = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.januar(2021), 31.januar(2021))
            on { getSumYtelse() } doReturn 0
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val desember = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 0
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val beregning = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(januar, desember)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE,
        )
    }

    @Test
    fun `beregning med alle måneder over minstebeløp skal ikke gi avslag`() {
        val januar = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.januar(2021), 31.januar(2021))
            on { getSumYtelse() } doReturn 18500
        }
        val desember = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 16400
        }
        val beregningSumYtelse = listOf(januar, desember).sumOf { it.getSumYtelse() }
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn beregningSumYtelse
            on { getMånedsberegninger() } doReturn listOf(januar, desember)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `avslag dersom det eksisterer 1 måned med beløp lik 0`() {
        val januar = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.januar(2021), 31.januar(2021))
            on { getSumYtelse() } doReturn 0
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val desember = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 2500
        }

        val beregning = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(januar, desember)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
    }

    @Test
    fun `avslag dersom det eksisterer 1 måned med beløp under minstegrense`() {
        val januar = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.januar(2021), 31.januar(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn false
            on { getSumYtelse() } doReturn 2500
        }
        val juni = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.juni(2021), 30.juni(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val desember = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { erSumYtelseUnderMinstebeløp() } doReturn false
            on { getSumYtelse() } doReturn 2500
        }

        val beregning = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(januar, juni, desember)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE,
        )
    }
}
