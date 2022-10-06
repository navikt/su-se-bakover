package no.nav.su.se.bakover.web

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResultatTest {
    @Test
    fun `equality tests`() {
        OK.errorJson("blabla", "din_feilkode_her") shouldBe OK.errorJson("blabla", "din_feilkode_her")
        OK.errorJson("blabla", "din_feilkode_her") shouldNotBe OK.errorJson("blabla2", "din_feilkode_her")
    }

    @Test
    fun `throw exception if unknown http status code`() {
        assertThrows<IllegalArgumentException> {
            Resultat.json(HttpStatusCode.fromValue(-1), """{}""")
        }
    }
}
