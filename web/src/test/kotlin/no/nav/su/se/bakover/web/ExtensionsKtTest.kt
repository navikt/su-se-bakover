package no.nav.su.se.bakover.web

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.infrastructure.web.toUUID
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ExtensionsKtTest {

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
