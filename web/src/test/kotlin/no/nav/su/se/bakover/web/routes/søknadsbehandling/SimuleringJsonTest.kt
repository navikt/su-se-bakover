package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.simulering
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
    private val expectedJson =
        """
        {
            "totalBruttoYtelse" : 30000,            
            "perioder": [
                    {
                      "kontooppstilling": {
                        "sumYtelse": 15000,
                        "debetFeilkonto": 0,
                        "kreditFeilkonto": 0,
                        "debetMotpostFeilkonto": 0,
                        "kreditMotpostFeilkonto": 0,
                        "sumMotpostFeilkonto": 0,
                        "kreditYtelse": 0,
                        "debetYtelse": 15000,
                        "simulertUtbetaling": 15000,
                        "sumFeilkonto": 0
                      },
                      "fraOgMed": "2020-01-01",
                      "tilOgMed": "2020-01-31"
                    },
                    {
                      "kontooppstilling": {
                        "sumYtelse": 15000,
                        "debetFeilkonto": 0,
                        "kreditFeilkonto": 0,
                        "debetMotpostFeilkonto": 0,
                        "kreditMotpostFeilkonto": 0,
                        "sumMotpostFeilkonto": 0,
                        "kreditYtelse": 0,
                        "debetYtelse": 15000,
                        "simulertUtbetaling": 15000,
                        "sumFeilkonto": 0
                      },
                      "fraOgMed": "2020-02-01",
                      "tilOgMed": "2020-02-29"
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
