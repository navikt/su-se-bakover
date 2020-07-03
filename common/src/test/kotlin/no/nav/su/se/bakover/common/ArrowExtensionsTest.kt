package no.nav.su.se.bakover.common

import arrow.core.Either
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class ArrowExtensionsTest {

    @Test
    fun `left and right value`() {
        Either.right("cake").rightValue() shouldBe "cake"
        Either.left("cake").leftValue() shouldBe "cake"
    }
}
