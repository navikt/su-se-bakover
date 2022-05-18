package no.nav.su.se.bakover.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class ExtensionsTest {

    @Test
    fun `sum limited to`() {
        123000.0.limitedUpwardsTo(500.0) shouldBe 500.0
        (-1234.0).limitedUpwardsTo(500.0) shouldBe -1234.0
    }

    @Test
    fun `sum positive or zero`() {
        123000.0.positiveOrZero() shouldBe 123000.0
        (-1234.0).positiveOrZero() shouldBe 0
    }

    @Test
    fun `round Double to amount of decimals`() {
        1234.1234.roundToDecimals(1) shouldBe 1234.1
        1234.1234.roundToDecimals(2) shouldBe 1234.12
        1234.1234.roundToDecimals(3) shouldBe 1234.123
        1234.1234.roundToDecimals(4) shouldBe 1234.1234

        1234.5678.roundToDecimals(1) shouldBe 1234.6
        1234.5678.roundToDecimals(2) shouldBe 1234.57
        1234.5678.roundToDecimals(3) shouldBe 1234.568
        1234.5678.roundToDecimals(4) shouldBe 1234.5678
    }

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
    fun `mapBoth`() {
        Pair(1, 2).mapBoth { it + 1 } shouldBe Pair(2, 3)
    }

    @Test
    fun `mapSecond`() {
        Pair(1, 2).mapSecond { it + 1 } shouldBe Pair(1, 3)
    }

    @Test
    fun `avrund`() {
        BigDecimal("1.00000000").avrund() shouldBe 1
        BigDecimal("1").avrund() shouldBe 1
        BigDecimal("1.1").avrund() shouldBe 1
    }
}
