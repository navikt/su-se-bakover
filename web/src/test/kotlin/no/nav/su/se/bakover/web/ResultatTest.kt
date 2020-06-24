package no.nav.su.se.bakover.web

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import no.nav.su.se.bakover.client.ClientResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ResultatTest {
    @Test
    fun `equalitytests`() {
        assertEquals(OK.tekst("blabla"), OK.tekst("blabla"))
        assertEquals(
                HttpStatusCode.Conflict.json("blabla"),
                HttpStatusCode.Conflict.json("blabla")
        )
        assertNotEquals(OK.tekst("blabla"), OK.tekst("blabla2"))
    }

    @Test
    fun `throw exception if unknown http status code`() {
        assertThrows<IllegalArgumentException> {
            Resultat.resultatMedJson(HttpStatusCode.fromValue(-1), """{}""")
        }
        assertThrows<IllegalArgumentException> {
            Resultat.resultatMedMelding(HttpStatusCode.fromValue(-1), """{}""")
        }
        assertThrows<IllegalArgumentException> {
            Resultat.from(ClientResponse(-1, """{}"""))
        }
    }
}