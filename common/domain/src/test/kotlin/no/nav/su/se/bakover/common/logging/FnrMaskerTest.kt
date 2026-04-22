package no.nav.su.se.bakover.common.logging

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class FnrMaskerTest {

    @Test
    fun `maskerer gyldig fnr i fritekst`() {
        FnrMasker.redact("fnr=07028820547") shouldBe "fnr=***********"
    }

    @Test
    fun `lar verdier som ikke matcher fnr-format sta uendret`() {
        FnrMasker.redact("verdi=1234567891") shouldBe "verdi=1234567891"
    }
}
