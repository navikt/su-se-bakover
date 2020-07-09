package no.nav.su.se.bakover.web.routes.sak

import io.kotest.assertions.json.shouldMatchJson
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SakJsonTest {

    private val sakId = UUID.randomUUID()
    private val sak = Sak(
        id = sakId,
        fnr = Fnr("12345678910")
    )

    //language=JSON
    val sakJsonString = """
            {
                "id": "$sakId",
                "fnr": "12345678910",
                "søknader": [],
                "behandlinger" : []
            }
        """.trimIndent()

    @Test
    fun `should serialize to json string`() {
        serialize(sak.toDto().toJson()) shouldMatchJson sakJsonString
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<SakJson>(sakJsonString) shouldBe sak.toDto().toJson()
    }
}
