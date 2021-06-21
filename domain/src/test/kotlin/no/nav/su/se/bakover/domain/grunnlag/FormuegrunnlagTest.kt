package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class FormuegrunnlagTest {

    @Test
    fun `Formue verdier kan ikke være negative`() {
        assertThrows<IllegalArgumentException> {
            Formuegrunnlag.Verdier(
                verdiIkkePrimærbolig = -1,
                verdiEiendommer = -2,
                verdiKjøretøy = -3,
                innskudd = -4,
                verdipapir = -5,
                pengerSkyldt = -6,
                kontanter = -7,
                depositumskonto = -8,
            )
        }
    }

    @Test
    fun `alle verdier som 0 skal bli 0`() {
        Formuegrunnlag.Verdier.empty().sumVerdier() shouldBe 0
    }

    @Test
    fun `dersom depositum er høyere enn innskud, blir ikke sum negativ`() {
        Formuegrunnlag.Verdier.empty().copy(
            innskudd = 100,
            depositumskonto = 200,
        ).sumVerdier() shouldBe 0
    }

    @Test
    fun `Depositum blir trekket fra innskud`() {
        Formuegrunnlag.Verdier.empty().copy(
            innskudd = 500,
            depositumskonto = 200,
        ).sumVerdier() shouldBe 300
    }

    @Test
    fun `Innskudd blir ikke trekket fra dersom depositum er 0`() {
        Formuegrunnlag.Verdier.empty().copy(
            innskudd = 500,
            depositumskonto = 0,
        ).sumVerdier() shouldBe 500
    }

}
