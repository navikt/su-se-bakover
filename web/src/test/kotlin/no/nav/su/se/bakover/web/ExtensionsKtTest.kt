package no.nav.su.se.bakover.web

import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.limitedUpwardsTo
import no.nav.su.se.bakover.common.positiveOrZero
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ExtensionsKtTest {

    @Test
    fun `String toUUID gir fin feilmelding`() {
        "heisann".toUUID() shouldBeLeft "heisann er ikke en gyldig UUID"
    }

    @Test
    fun `String toUUID funker på gyldig UUID`() {
        UUID.randomUUID().let {
            it.toString().toUUID() shouldBeRight it
        }
    }

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
}
