package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.Either
import no.nav.su.se.bakover.Fødselsnummer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class FødselsnummerTest {
    @Test
    fun `riktig lengde`() {
        Fødselsnummer.fraString(null).fold(
                left = { assertTrue(true) },
                right = { fail("Fnr er for kort") }
        )
        Fødselsnummer.fraString("12345").fold(
                left = { assertTrue(true) },
                right = { fail("Fnr er for kort") }
        )
        Fødselsnummer.fraString("1234567891012").fold(
                left = { assertTrue(true) },
                right = { fail("Fnr er for langt") }
        )
        Fødselsnummer.fraString("12345678910").fold(
                left = { fail("Burde være ok") as Either.Left<*> },
                right = { assertTrue(true) }
        )
    }

    @Test
    fun `må inneholde siffer`() {
        Fødselsnummer.fraString("qwertyuiopå").fold(
                left = { assertTrue(true) },
                right = { fail("Fnr kan kun inneholde siffer") }
        )
        Fødselsnummer.fraString("12345678910").fold(
                left = { fail("Burde være ok") as Either.Left<*> },
                right = { assertTrue(true) }
        )
    }
}