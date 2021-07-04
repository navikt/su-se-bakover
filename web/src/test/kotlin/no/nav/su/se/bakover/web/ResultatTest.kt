package no.nav.su.se.bakover.web

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResultatTest {
    @Test
    fun `equality tests`() {
        OK.message("blabla") shouldBe OK.message("blabla")
        OK.message("blabla") shouldNotBe OK.message("blabla2")
    }

    @Test
    fun `throw exception if unknown http status code`() {
        assertThrows<IllegalArgumentException> {
            Resultat.json(HttpStatusCode.fromValue(-1), """{}""")
        }
        assertThrows<IllegalArgumentException> {
            Resultat.message(HttpStatusCode.fromValue(-1), """{}""")
        }
    }
}
