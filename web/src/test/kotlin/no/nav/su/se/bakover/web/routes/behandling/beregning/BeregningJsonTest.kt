package no.nav.su.se.bakover.web.routes.behandling.beregning

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.web.routes.behandling.TestBeregning
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
                    "beløp":19946,
                    "satsbeløp": 20637,
                    "fradrag": [{
                      "periode": {
                        "fraOgMed":"2020-08-01",
                        "tilOgMed":"2020-08-31"
                      },
                      "type": "Arbeidsinntekt",
                      "beløp": 1000.0,
                      "tilhører": "BRUKER",
                      "utenlandskInntekt": null
                    }, {
                      "periode": {
                        "fraOgMed":"2020-08-01",
                        "tilOgMed":"2020-08-31"
                      },
                      "type": "Arbeidsinntekt",
                      "beløp": 20000.0,
                      "tilhører": "EPS",
                      "utenlandskInntekt": null
                    }],
                    "epsFribeløp": 0.0,
                    "epsInputFradrag": [{
                      "periode": {
                        "fraOgMed":"2020-08-01",
                        "tilOgMed":"2020-08-31"
                      },
                      "type": "Arbeidsinntekt",
                      "beløp": 20000.0,
                      "tilhører": "EPS",
                      "utenlandskInntekt": null
                    }]
                }],
                "fradrag": [{
                  "type": "Arbeidsinntekt",
                  "beløp": 1000.0,
                  "utenlandskInntekt": null,
                  "periode" : {
                    "fraOgMed":"2020-08-01",
                    "tilOgMed":"2020-08-31"
                  },
                  "tilhører": "BRUKER"
                }, {
                  "type": "Arbeidsinntekt",
                  "beløp": 20000.0,
                  "utenlandskInntekt": null,
                  "periode" : {
                    "fraOgMed":"2020-08-01",
                    "tilOgMed":"2020-08-31"
                  },
                  "tilhører": "EPS"
                }]
            }
        """
    }

    @Test
    fun json() {
        JSONAssert.assertEquals(expectedBeregningJson.trimIndent(), serialize(TestBeregning.toJson()), true)
    }
}
