package no.nav.su.se.bakover.web.routes.sak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class SakJsonTest {

    private val sakId = UUID.randomUUID()
    private val sak = Sak(
        id = sakId,
        fnr = Fnr("12345678910"),
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId
        )
    )

    //language=JSON
    val sakJsonString =
        """
            {
                "id": "$sakId",
                "fnr": "12345678910",
                "søknader": [],
                "behandlinger" : [],
                "utbetalinger": [],
                "utbetalingerKanStansesEllerGjenopptas": "INGEN"
            }
        """.trimIndent()

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(sakJsonString, serialize(sak.toJson()), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<SakJson>(sakJsonString) shouldBe sak.toJson()
    }
}
