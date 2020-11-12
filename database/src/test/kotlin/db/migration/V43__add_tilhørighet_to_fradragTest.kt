package db.migration

import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class V43__add_tilhørighet_to_fradragTest {

    @Test
    fun `skal legge til tilhører bruker på alle fradrag`() {
        val migrated = AddTilhørerToFradrag.migrate(mapOf("id" to toMigrate))
        JSONAssert.assertEquals(expected, migrated["id"], true)
    }

    //language=json
    private val toMigrate = """
        {
          "id": "beb1e09b-57cf-4ebe-b5a9-25e822c71232",
          "sats": "HØY",
          "fradrag": [
            {
              "periode": {
                "fraOgMed": "2020-01-01",
                "tilOgMed": "2020-12-31",
                "antallMåneder": 12
              },
              "fradragstype": "Arbeidsinntekt",
              "totaltFradrag": 12000.0,
              "fradragPerMåned": 1000.0,
              "utenlandskInntekt": null
            }
          ],
          "periode": {
            "fraOgMed": "2020-01-01",
            "tilOgMed": "2020-12-31",
            "antallMåneder": 12
          },
          "opprettet": "2020-11-11T09:58:08.811549Z",
          "sumYtelse": 238116,
          "sumFradrag": 12000.0,
          "månedsberegninger": [
            {
              "sats": "HØY",
              "fradrag": [
                {
                  "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-01-31",
                    "antallMåneder": 1
                  },
                  "fradragstype": "Arbeidsinntekt",
                  "totaltFradrag": 1000.0,
                  "fradragPerMåned": 1000.0,
                  "utenlandskInntekt": null
                }
              ],
              "periode": {
                "fraOgMed": "2020-01-01",
                "tilOgMed": "2020-01-31",
                "antallMåneder": 1
              },
              "sumYtelse": 19637,
              "satsbeløp": 20637.32,
              "sumFradrag": 1000.0,
              "benyttetGrunnbeløp": 99858
            }
          ]
        }
    """.trimIndent()

    //language=json
    private val expected = """
        {
          "id": "beb1e09b-57cf-4ebe-b5a9-25e822c71232",
          "sats": "HØY",
          "fradrag": [
            {
              "periode": {
                "fraOgMed": "2020-01-01",
                "tilOgMed": "2020-12-31",
                "antallMåneder": 12
              },
              "fradragstype": "Arbeidsinntekt",
              "totaltFradrag": 12000.0,
              "fradragPerMåned": 1000.0,
              "utenlandskInntekt": null,
              "tilhører": "BRUKER"
            }
          ],
          "periode": {
            "fraOgMed": "2020-01-01",
            "tilOgMed": "2020-12-31",
            "antallMåneder": 12
          },
          "opprettet": "2020-11-11T09:58:08.811549Z",
          "sumYtelse": 238116,
          "sumFradrag": 12000.0,
          "månedsberegninger": [
            {
              "sats": "HØY",
              "fradrag": [
                {
                  "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-01-31",
                    "antallMåneder": 1
                  },
                  "fradragstype": "Arbeidsinntekt",
                  "totaltFradrag": 1000.0,
                  "fradragPerMåned": 1000.0,
                  "utenlandskInntekt": null,
                  "tilhører": "BRUKER"
                }
              ],
              "periode": {
                "fraOgMed": "2020-01-01",
                "tilOgMed": "2020-01-31",
                "antallMåneder": 1
              },
              "sumYtelse": 19637,
              "satsbeløp": 20637.32,
              "sumFradrag": 1000.0,
              "benyttetGrunnbeløp": 99858
            }
          ]
        }
    """.trimIndent()
}
