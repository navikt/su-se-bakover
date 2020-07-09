package no.nav.su.se.bakover.common

import arrow.core.Either
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import org.junit.jupiter.api.Test

internal class ArrowExtensionsTest {

    @Test
    fun `left and right value`() {
        Either.right("cake") shouldBeRight "cake"
        Either.left("cake") shouldBeLeft "cake"
    }
}
