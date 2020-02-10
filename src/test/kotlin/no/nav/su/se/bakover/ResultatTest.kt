package no.nav.su.se.bakover

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ResultatTest {
    @Test
    fun `equalitytests`() {
        assertEquals(Resultat.ok("blabla"), Resultat.ok("blabla"))
        assertEquals(
            Resultat.feilMedMelding(HttpStatusCode.Conflict, "blabla"),
            Resultat.feilMedMelding(HttpStatusCode.Conflict, "blabla")
        )
        assertNotEquals<Resultat>(Resultat.ok("blabla"), Resultat.ok("blabla2"))
    }
}