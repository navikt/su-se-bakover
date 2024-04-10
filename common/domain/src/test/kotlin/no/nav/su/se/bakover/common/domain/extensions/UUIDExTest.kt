package no.nav.su.se.bakover.common.domain.extensions

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UUIDExTest {
    @Test
    fun `String toUUID gir fin feilmelding`() {
        "heisann".toUUID() shouldBe "heisann er ikke en gyldig UUID".left()
    }

    @Test
    fun `String toUUID funker på gyldig UUID`() {
        UUID.randomUUID().let {
            it.toString().toUUID() shouldBe it.right()
        }
    }
}
