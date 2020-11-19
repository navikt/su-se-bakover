package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.common.serialize
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class BeregningJsonTest {
    companion object {
        //language=JSON
        internal val expectedBeregningJson =
            """
            {
                "id":"${TestBeregning.getId()}",
                "opprettet":"2020-08-01T12:15:15Z",
                "fraOgMed":"2020-08-01",
                "tilOgMed":"2020-08-31",
                "sats":"HØY",
                "månedsberegninger": [{
                    "fraOgMed":"2020-08-01",
                    "tilOgMed":"2020-08-31",
                    "sats":"HØY",
                    "grunnbeløp":101351,
                    "beløp":19946
                }],
                "fradrag": [{
                  "type": "Arbeidsinntekt",
                  "beløp": 1000.0,
                  "utenlandskInntekt": null,
                  "periode" : {
                    "fraOgMed":"2020-08-01",
                    "tilOgMed":"2020-08-31"
                  }
                }]
            }
        """
    }

    @Test
    fun json() {
        JSONAssert.assertEquals(expectedBeregningJson.trimIndent(), serialize(TestBeregning.toJson()), true)
    }
}
