package no.nav.su.se.bakover.domain.behandling

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
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
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn 0
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            avslagsgrunn = Avslagsgrunn.FOR_HØY_INNTEKT
        )
    }

    @Test
    fun `beregning med negativ sum ytelse skal gi avslag`() {
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn -4500
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            avslagsgrunn = Avslagsgrunn.FOR_HØY_INNTEKT
        )
    }

    @Test
    fun `beregning med alle måneder under minstebeløp skal gi avslag`() {
        val januar = mock<Månedsberegning> {
            on { getPeriode() } doReturn Periode(1.januar(2020), 31.januar(2020))
            on { getSumYtelse() } doReturn 250
        }
        val desember = mock<Månedsberegning> {
            on { getPeriode() } doReturn Periode(1.desember(2020), 31.desember(2020))
            on { getSumYtelse() } doReturn 250
        }
        val beregningSumYtelse = listOf(januar, desember).sumBy { it.getSumYtelse() }
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn beregningSumYtelse
            on { getMånedsberegninger() } doReturn listOf(januar, desember)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            avslagsgrunn = Avslagsgrunn.SU_UNDER_MINSTEGRENSE
        )
    }

    @Test
    fun `beregning med alle måneder over minstebeløp skal ikke gi avslag`() {
        val januar = mock<Månedsberegning> {
            on { getPeriode() } doReturn Periode(1.januar(2020), 31.januar(2020))
            on { getSumYtelse() } doReturn 18500
        }
        val desember = mock<Månedsberegning> {
            on { getPeriode() } doReturn Periode(1.desember(2020), 31.desember(2020))
            on { getSumYtelse() } doReturn 16400
        }
        val beregningSumYtelse = listOf(januar, desember).sumBy { it.getSumYtelse() }
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn beregningSumYtelse
            on { getMånedsberegninger() } doReturn listOf(januar, desember)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `beregning med en måned under minstebeløp skal gi avslag`() {
        val januar = mock<Månedsberegning> {
            on { getPeriode() } doReturn Periode(1.januar(2020), 31.januar(2020))
            on { getSumYtelse() } doReturn 2500
        }
        val desember = mock<Månedsberegning> {
            on { getPeriode() } doReturn Periode(1.desember(2020), 31.desember(2020))
            on { getSumYtelse() } doReturn 250
        }

        val beregningSumYtelse = listOf(januar, desember).sumBy { it.getSumYtelse() }
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn beregningSumYtelse
            on { getMånedsberegninger() } doReturn listOf(januar, desember)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            avslagsgrunn = Avslagsgrunn.SU_UNDER_MINSTEGRENSE
        )
    }

    @Test
    fun `nærmeste krone mindre enn minstebeløp skal gi avslag - januar`() {
        val januar = mock<Månedsberegning> {
            on { getPeriode() } doReturn Periode(1.januar(2020), 31.januar(2020))
            on { getSumYtelse() } doReturn 412
        }

        val beregningSumYtelse = listOf(januar).sumBy { it.getSumYtelse() }
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn beregningSumYtelse
            on { getMånedsberegninger() } doReturn listOf(januar)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            avslagsgrunn = Avslagsgrunn.SU_UNDER_MINSTEGRENSE
        )
    }

    @Test
    fun `nærmeste krone mindre enn minstebeløp skal gi avslag - desember`() {
        val desember = mock<Månedsberegning> {
            on { getPeriode() } doReturn Periode(1.desember(2020), 31.desember(2020))
            on { getSumYtelse() } doReturn 418
        }

        val beregningSumYtelse = listOf(desember).sumBy { it.getSumYtelse() }
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn beregningSumYtelse
            on { getMånedsberegninger() } doReturn listOf(desember)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            avslagsgrunn = Avslagsgrunn.SU_UNDER_MINSTEGRENSE
        )
    }

    @Test
    fun `nærmeste krone større enn minstebeløp skal ikke gi avslag - januar`() {
        val januar = mock<Månedsberegning> {
            on { getPeriode() } doReturn Periode(1.januar(2020), 31.januar(2020))
            on { getSumYtelse() } doReturn 413
        }

        val beregningSumYtelse = listOf(januar).sumBy { it.getSumYtelse() }
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn beregningSumYtelse
            on { getMånedsberegninger() } doReturn listOf(januar)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `nærmeste krone større enn minstebeløp skal ikke gi avslag - desember`() {
        val desember = mock<Månedsberegning> {
            on { getPeriode() } doReturn Periode(1.desember(2020), 31.desember(2020))
            on { getSumYtelse() } doReturn 419
        }

        val beregningSumYtelse = listOf(desember).sumBy { it.getSumYtelse() }
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn beregningSumYtelse
            on { getMånedsberegninger() } doReturn listOf(desember)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `sum ytelse vurders før minstegrense`() {
        val januar = mock<Månedsberegning> {
            on { getPeriode() } doReturn Periode(1.januar(2020), 31.januar(2020))
            on { getSumYtelse() } doReturn 250
        }

        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn 0
            on { getMånedsberegninger() } doReturn listOf(januar)
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            avslagsgrunn = Avslagsgrunn.FOR_HØY_INNTEKT
        )
    }
}
