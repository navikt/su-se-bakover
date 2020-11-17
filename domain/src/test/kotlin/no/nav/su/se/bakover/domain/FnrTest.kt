package no.nav.su.se.bakover.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class FnrTest {
    @Test
    fun `validity`() {
        assertThrows<UgyldigFnrException> { Fnr("12345") }
        assertThrows<UgyldigFnrException> { Fnr("1234567891012") }
        assertThrows<UgyldigFnrException> { Fnr("qwertyuiopå") }
        assertDoesNotThrow { Fnr("12345678910") }
    }

    @Test
    fun `får ut riktig århundre for fnr`() {
        Fnr("16113113816").getÅrhundre().shouldBe("19")
        Fnr("16114095016").getÅrhundre().shouldBe("19")
        Fnr("01011050001").getÅrhundre().shouldBe("20")
        Fnr("01016050001").getÅrhundre().shouldBe("18")
    }

    @Test
    fun `får ut riktig individnummer`() {
        Fnr("16113113816").getIndividnummer().shouldBe("138")
    }

    @Test
    fun `får ut riktig fødselsdag`() {
        Fnr("16113113816").getFødselsdag().shouldBe("16")
    }

    @Test
    fun `får ut riktig fødselsmåned`() {
        Fnr("16113113816").getFødselsmåned().shouldBe("11")
    }

    @Test
    fun `får ut riktig fødselsår`() {
        Fnr("16113113816").getFødselsår().shouldBe("31")
    }
}
