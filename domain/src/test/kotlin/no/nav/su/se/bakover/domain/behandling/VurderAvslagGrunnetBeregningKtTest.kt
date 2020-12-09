package no.nav.su.se.bakover.domain.behandling

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
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
    fun `beregning med sum ytelse under minstebeløp skal gi avslag`() {
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn 250
            on { getSumYtelseErUnderMinstebeløp() } doReturn true
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            avslagsgrunn = Avslagsgrunn.SU_UNDER_MINSTEGRENSE
        )
    }

    @Test
    fun `beregning med sum ytelse over minstebeløp skal ikke gi avslag`() {
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn 250
            on { getSumYtelseErUnderMinstebeløp() } doReturn false
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `sum ytelse vurders før minstegrense`() {
        val beregning = mock<Beregning> {
            on { getSumYtelse() } doReturn 0
            on { getSumYtelseErUnderMinstebeløp() } doReturn true
        }
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            avslagsgrunn = Avslagsgrunn.FOR_HØY_INNTEKT
        )
    }
}
