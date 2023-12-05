package no.nav.su.se.bakover.common.domain

import no.nav.su.se.bakover.common.domain.NonBlankString.Companion.toNonBlankString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class NonBlankStringTest {

    @Test
    fun `exception dersom strengen er blank`() {
        assertThrows<IllegalArgumentException> {
            NonBlankString.create(" ")
            " ".toNonBlankString()
        }
    }

    @Test
    fun `konstruerer NonBlankString`() {
        assertDoesNotThrow {
            NonBlankString.create("a")
            "a".toNonBlankString()
        }
    }
}
