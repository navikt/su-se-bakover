package no.nav.su.se.bakover.common.domain.extensions

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class DoubleExTest {
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
}
