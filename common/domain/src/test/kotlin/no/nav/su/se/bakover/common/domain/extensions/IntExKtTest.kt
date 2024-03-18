package no.nav.su.se.bakover.common.domain.extensions

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class IntExKtTest {
    @Test
    fun `toStringWithDecimals should return the integer as a string with the specified number of decimal places`() {
        42.toStringWithDecimals(2) shouldBe "42.00"
        42.toStringWithDecimals(0) shouldBe "42"
        42.toStringWithDecimals(5) shouldBe "42.00000"
    }
}
