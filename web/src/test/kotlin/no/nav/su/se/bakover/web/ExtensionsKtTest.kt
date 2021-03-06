package no.nav.su.se.bakover.web

import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.limitedUpwardsTo
import no.nav.su.se.bakover.common.positiveOrZero
import no.nav.su.se.bakover.common.roundToDecimals
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ExtensionsKtTest {

    @Test
    fun `String toUUID gir fin feilmelding`() {
        runBlocking {
            "heisann".toUUID() shouldBeLeft "heisann er ikke en gyldig UUID"
        }
    }

    @Test
    fun `String toUUID funker på gyldig UUID`() {
        runBlocking {
            UUID.randomUUID().let {
                it.toString().toUUID() shouldBeRight it
            }
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

    @Test
    fun `round to amount of decimals`() {
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
