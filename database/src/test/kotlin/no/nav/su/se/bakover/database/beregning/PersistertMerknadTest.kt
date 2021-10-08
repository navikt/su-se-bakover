package no.nav.su.se.bakover.database.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class PersistertMerknadTest {

    @Test
    fun `serialisering og av merknad for endret grunnbeløp`() {
        //language=json
        val expected = """
            {
              "type": "EndringGrunnbeløp",
              "gammeltGrunnbeløp": {
                "dato": "2020-05-01",
                "grunnbeløp": 101351
              },
              "nyttGrunnbeløp": {
                "dato": "2021-05-01",
                "grunnbeløp": 106399
              }
            }
        """.trimIndent()

        val merknad = Merknad.EndringGrunnbeløp(
            gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj.forDato(1.mai(2020)),
            nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj.forDato(1.mai(2021)),
        )
        JSONAssert.assertEquals(expected, serialize(merknad.toSnapshot()), true)

        merknad shouldBe merknad.toSnapshot().toDomain()
    }

    @Test
    fun `serialisering og av merknad for økt, redusert eller uendret`() {
        //language=json
        val expected = """
            {
              "type": "merknadtype",
              "benyttetBeregning": {
                "periode": {
                  "fraOgMed": "2021-05-01",
                  "tilOgMed": "2021-05-31"
                },
                "sats": "HØY",
                "grunnbeløp": 106399,
                "beløp": 16989,
                "fradrag": [
                  {
                    "periode": {
                      "fraOgMed": "2021-05-01",
                      "tilOgMed": "2021-05-31"
                    },
                    "fradragstype": "PrivatPensjon",
                    "månedsbeløp": 5000,
                    "utenlandskInntekt": null,
                    "tilhører": "BRUKER"
                  }  
                ],
                "satsbeløp": 21989.126666666667,
                "fribeløpForEps": 0.0
              },
              "forkastetBeregning": {
                "periode": {
                  "fraOgMed": "2021-05-01",
                  "tilOgMed": "2021-05-31"
                },
                "sats": "ORDINÆR",
                "grunnbeløp": 106399,
                "beløp": 2216,
                "fradrag": [
                  {
                    "periode": {
                      "fraOgMed": "2021-05-01",
                      "tilOgMed": "2021-05-31"
                    },
                    "fradragstype": "Sosialstønad",
                    "månedsbeløp": 18000,
                    "utenlandskInntekt": null,
                    "tilhører": "EPS"
                  }  
                ],
                "satsbeløp": 20215.809999999998,
                "fribeløpForEps": 3000.0
              }
            }
        """.trimIndent()

        val benyttetBeregning = MånedsberegningFactory.ny(
            periode = mai(2021),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.PrivatPensjon,
                    månedsbeløp = 5000.0,
                    periode = mai(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fribeløpForEps = 0.0,
        )
        val forkastetBeregning = MånedsberegningFactory.ny(
            periode = mai(2021),
            sats = Sats.ORDINÆR,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 18000.0,
                    periode = mai(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
            fribeløpForEps = 3000.0,
        )

        val øktYtelse = Merknad.ØktYtelse.from(benyttetBeregning, forkastetBeregning)
        val redusertYtelse = Merknad.RedusertYtelse.from(benyttetBeregning, forkastetBeregning)
        val endringUnderTiProsent = Merknad.EndringUnderTiProsent.from(benyttetBeregning, forkastetBeregning)

        JSONAssert.assertEquals(
            expected.replace("merknadtype", "ØktYtelse"),
            serialize(øktYtelse.toSnapshot()),
            true,
        )
        øktYtelse shouldBe øktYtelse.toSnapshot().toDomain()

        JSONAssert.assertEquals(
            expected.replace("merknadtype", "RedusertYtelse"),
            serialize(redusertYtelse.toSnapshot()),
            true,
        )
        redusertYtelse shouldBe redusertYtelse.toSnapshot().toDomain()

        JSONAssert.assertEquals(
            expected.replace("merknadtype", "EndringUnderTiProsent"),
            serialize(endringUnderTiProsent.toSnapshot()),
            true,
        )

        endringUnderTiProsent shouldBe endringUnderTiProsent.toSnapshot().toDomain()
    }

    @Test
    fun `serialisering og av merknad for ny ytelse`() {
        //language=json
        val expected = """
            {
              "type": "NyYtelse",
              "benyttetBeregning": {
                "periode": {
                  "fraOgMed": "2021-05-01",
                  "tilOgMed": "2021-05-31"
                },
                "sats": "HØY",
                "grunnbeløp": 106399,
                "beløp": 16989,
                "fradrag": [
                  {
                    "periode": {
                      "fraOgMed": "2021-05-01",
                      "tilOgMed": "2021-05-31"
                    },
                    "fradragstype": "PrivatPensjon",
                    "månedsbeløp": 5000,
                    "utenlandskInntekt": null,
                    "tilhører": "BRUKER"
                  }  
                ],
                "satsbeløp": 21989.126666666667,
                "fribeløpForEps": 0.0
              }
            }
        """.trimIndent()

        val benyttetBeregning = MånedsberegningFactory.ny(
            periode = mai(2021),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.PrivatPensjon,
                    månedsbeløp = 5000.0,
                    periode = mai(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fribeløpForEps = 0.0,
        )

        val nyYtelse = Merknad.NyYtelse.from(benyttetBeregning)

        JSONAssert.assertEquals(
            expected,
            serialize(nyYtelse.toSnapshot()),
            true,
        )

        nyYtelse shouldBe nyYtelse.toSnapshot().toDomain()
    }
}
