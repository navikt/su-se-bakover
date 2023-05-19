package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.simulering.simulering
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class SimuleringJsonTest {
    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(expectedJson, serialize(simulering.toJson()), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<SimuleringJson>(expectedJson) shouldBe simulering.toJson()
    }

    //language=JSON
    private val expectedJson = """
        {
          "totalOppsummering": {
            "fraOgMed": "2020-01-01",
            "tilOgMed": "2020-02-29",
            "sumTilUtbetaling": 30000,
            "sumEtterbetaling": 0,
            "sumFramtidigUtbetaling": 30000,
            "sumTotalUtbetaling": 30000,
            "sumTidligereUtbetalt": 0,
            "sumFeilutbetaling": 0,
            "sumReduksjonFeilkonto": 0
          },
          "periodeOppsummering": [
            {
              "fraOgMed": "2020-01-01",
              "tilOgMed": "2020-01-31",
              "sumTilUtbetaling": 15000,
              "sumEtterbetaling": 0,
              "sumFramtidigUtbetaling": 15000,
              "sumTotalUtbetaling": 15000,
              "sumTidligereUtbetalt": 0,
              "sumFeilutbetaling": 0,
              "sumReduksjonFeilkonto": 0
            },
            {
              "fraOgMed": "2020-02-01",
              "tilOgMed": "2020-02-29",
              "sumTilUtbetaling": 15000,
              "sumEtterbetaling": 0,
              "sumFramtidigUtbetaling": 15000,
              "sumTotalUtbetaling": 15000,
              "sumTidligereUtbetalt": 0,
              "sumFeilutbetaling": 0,
              "sumReduksjonFeilkonto": 0
            }
          ]
        }
    """.trimIndent()

    private val simulering = simulering(
        perioder = listOf(
            januar(2020),
            februar(2020),
        ),
    )
}
