package no.nav.su.se.bakover.web.routes.søknad.kontrakt

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.søknadId
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.web.Frontendkontrakt
import no.nav.su.se.bakover.web.routes.søknad.LukketJson
import no.nav.su.se.bakover.web.routes.søknad.toLukketJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

@Frontendkontrakt
internal class UføresøknadJsonTest {
    @Test
    fun `serialiserer og deserialiserer lukket`() {
        val trukket = nySakMedjournalførtSøknadOgOppgave(
            søknadId = søknadId,
        ).second.lukk(
            trekkSøknad(søknadId),
        )
        //language=json
        val expectedJson = """
            {
                "tidspunkt":"2021-01-01T01:02:03.456789Z",
                "saksbehandler":"saksbehandler",
                "type":"TRUKKET",
                "dokumenttilstand": "IKKE_GENERERT_ENDA"
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, serialize(trukket.toLukketJson()), true)
        deserialize<LukketJson>(expectedJson) shouldBe LukketJson(
            tidspunkt = "2021-01-01T01:02:03.456789Z",
            saksbehandler = "saksbehandler",
            type = "TRUKKET",
            dokumenttilstand = "IKKE_GENERERT_ENDA",
        )
    }
}
