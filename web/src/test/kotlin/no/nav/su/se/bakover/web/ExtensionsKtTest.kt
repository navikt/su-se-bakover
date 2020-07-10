package no.nav.su.se.bakover.web

import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
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
}
