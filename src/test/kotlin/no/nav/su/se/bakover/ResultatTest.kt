package no.nav.su.se.bakover

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import org.junit.jupiter.api.Test
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
}