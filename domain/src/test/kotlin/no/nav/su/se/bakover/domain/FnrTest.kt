package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.Fnr
import no.nav.su.se.bakover.UgyldigFnrException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class FnrTest {
    @Test
    fun `validity`() {
        assertThrows<UgyldigFnrException> { Fnr(null) }
        assertThrows<UgyldigFnrException> { Fnr("12345") }
        assertThrows<UgyldigFnrException> { Fnr("1234567891012") }
        assertThrows<UgyldigFnrException> { Fnr("qwertyuiopå") }
        assertDoesNotThrow { Fnr("12345678910") }
    }
}