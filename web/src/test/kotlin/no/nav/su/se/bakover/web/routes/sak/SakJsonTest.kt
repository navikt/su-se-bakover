package no.nav.su.se.bakover.web.routes.sak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class SakJsonTest {

    private val sakId = UUID.randomUUID()
    private val sak = Sak(
        id = sakId,
        fnr = Fnr("12345678910")
    )

    //language=JSON
    val sakJsonString =
        """
            {
                "id": "$sakId",
                "fnr": "12345678910",
                "søknader": [],
                "behandlinger" : []
            }
        """.trimIndent()

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(sakJsonString, serialize(sak.toDto().toJson()), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<SakJson>(sakJsonString) shouldBe sak.toDto().toJson()
    }
}
