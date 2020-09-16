package no.nav.su.se.bakover.web.routes.behandllinger.stopp

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Instant

internal class StoppbehandlingJsonTest {

    private val id = "2cf19c5a-716f-4dce-86d9-2d0b23b7ca1e"
    private val sakId = "6c048a85-3618-48ca-a95a-dbe80814e22d"

    @Nested
    inner class OpprettetStoppbehandlingTest {
        val jsonObject = StoppbehandlingJson(
            id = id,
            opprettet = Instant.EPOCH,
            sakId = sakId,
            status = "OPPRETTET"
        )
        val jsonString = //language=JSON
            """
            {
            "id":"$id",
            "opprettet":"1970-01-01T00:00:00Z",
            "sakId":"$sakId",
            "status":"OPPRETTET"
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
