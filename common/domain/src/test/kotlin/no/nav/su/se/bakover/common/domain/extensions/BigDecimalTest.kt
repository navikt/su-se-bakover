package no.nav.su.se.bakover.common.domain.extensions

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class BigDecimalTest {
    @Test
    fun `round BigDecimal to amount of decimals`() {
        BigDecimal("1234.1234").roundToDecimals(1) shouldBe 1234.1
        BigDecimal("1234.1234").roundToDecimals(2) shouldBe 1234.12
        BigDecimal("1234.1234").roundToDecimals(3) shouldBe 1234.123
        BigDecimal("1234.1234").roundToDecimals(4) shouldBe 1234.1234

        BigDecimal("1234.5678").roundToDecimals(1) shouldBe 1234.6
        BigDecimal("1234.5678").roundToDecimals(2) shouldBe 1234.57
        BigDecimal("1234.5678").roundToDecimals(3) shouldBe 1234.568
        BigDecimal("1234.5678").roundToDecimals(4) shouldBe 1234.5678
    }

    @Test
    fun `round BigDecimal to 4`() {
        BigDecimal("1234.12345").scaleTo4() shouldBe BigDecimal("1234.1235")
        BigDecimal("1234.12344").scaleTo4() shouldBe BigDecimal("1234.1234")
        BigDecimal("1234.123").scaleTo4() shouldBe BigDecimal("1234.1230")
        BigDecimal("1234").scaleTo4() shouldBe BigDecimal("1234.0000")
    }

    @Test
    fun `avrund`() {
        BigDecimal("1.00000000").avrund() shouldBe 1
        BigDecimal("1").avrund() shouldBe 1
        BigDecimal("1.1").avrund() shouldBe 1
    }
}
