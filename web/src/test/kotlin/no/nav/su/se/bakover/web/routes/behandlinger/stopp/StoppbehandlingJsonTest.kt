package no.nav.su.se.bakover.web.routes.behandlinger.stopp

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.MicroInstant
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class StoppbehandlingJsonTest {

    private val stoppbehandlingId = "2cf19c5a-716f-4dce-86d9-2d0b23b7ca1e"
    private val utbetalingId = "08717d94-f305-4fe3-bb58-22fbcb7f63ec"
    private val sakId = "6c048a85-3618-48ca-a95a-dbe80814e22d"

    @Nested
    inner class OpprettetStoppbehandlingTest {
        val jsonObject = StoppbehandlingJson(
            id = stoppbehandlingId,
            opprettet = MicroInstant.EPOCH,
            sakId = sakId,
            status = "OPPRETTET",
            utbetaling = UtbetalingJson(
                id = utbetalingId,
                opprettet = "1970-01-01T00:00:00Z",
                simulering = null
            ),
            stoppÅrsak = "stoppÅrsak",
            saksbehandler = "saksbehandler"
        )
        val jsonString = //language=JSON
            """
            {
            "id":"$stoppbehandlingId",
            "opprettet":"1970-01-01T00:00:00Z",
            "sakId":"$sakId",
            "status":"OPPRETTET",
            "utbetaling": {
              "id": "$utbetalingId",
              "opprettet": "1970-01-01T00:00:00Z",
              "simulering": null
            },
            "stoppÅrsak": "stoppÅrsak",
            "saksbehandler": "saksbehandler"
            }
            """.trimIndent()

        @Test
        internal fun `serialize opprettet stoppbehandling`() {
            JSONAssert.assertEquals(
                jsonString,
                serialize(jsonObject),
                true
            )
        }

        @Test
        internal fun `deserialize opprettet stoppbehandling`() {
            deserialize<StoppbehandlingJson>(jsonString) shouldBe jsonObject
        }
    }
}
