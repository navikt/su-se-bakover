package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LagBrevinnholdForBeregningTest {
    @Test
    fun `jsonformat på beregning stemmer overens med det som forventes av pdfgenerator`() {
        val beregning = Beregning(
            ytelsePerMåned = 100,
            satsbeløpPerMåned = 100,
            epsFribeløp = 100.0,
            fradrag = Fradrag(
                bruker = FradragForBruker(
                    fradrag = listOf(
                        Månedsfradrag(
                            type = Fradragstype.Arbeidsinntekt.toReadableTypeName(utenlandsk = false),
                            beløp = 10.0,
                            utenlandskInntekt = null
                        ),
                        Månedsfradrag(
                            type = Fradragstype.OffentligPensjon.toReadableTypeName(utenlandsk = false),
                            beløp = 35.0,
                            utenlandskInntekt = null
                        ),
                        Månedsfradrag(
                            type = Fradragstype.OffentligPensjon.toReadableTypeName(utenlandsk = true),
                            beløp = 70.0,
                            utenlandskInntekt = UtenlandskInntekt(
                                beløpIUtenlandskValuta = 140,
                                valuta = "SEK",
                                kurs = 0.5
                            )
                        )
                    ),
                    sum = 45.0,
                    harBruktForventetInntektIStedetForArbeidsinntekt = false
                ),
                eps = FradragForEps(
                    fradrag = listOf(
                        Månedsfradrag(
                            type = Fradragstype.Arbeidsinntekt.toReadableTypeName(utenlandsk = false),
                            beløp = 20.0,
                            utenlandskInntekt = null
                        ),
                        Månedsfradrag(
                            type = Fradragstype.OffentligPensjon.toReadableTypeName(utenlandsk = false),
                            beløp = 70.0,
                            utenlandskInntekt = null
                        )
                    ),
                    sum = 0.0
                )
            ),
        )

        //language=json
        val expectedJson = """
            {
              "ytelsePerMåned": 100,
              "satsbeløpPerMåned": 100,
              "epsFribeløp": 100.0,
              "fradrag": {
                "bruker": {
                  "fradrag": [
                    {
                      "type": "Arbeidsinntekt",
                      "beløp": 10.0,
                      "utenlandskInntekt": null
                    },
                    {
                      "type": "Offentlig pensjon",
                      "beløp": 35.0,
                      "utenlandskInntekt": null
                    },
                    {
                      "type": "Offentlig pensjon — fra utlandet",
                      "beløp": 70.0,
                      "utenlandskInntekt": {
                        "beløpIUtenlandskValuta": 140,
                        "valuta": "SEK",
                        "kurs": 0.5
                      }
                    }
                  ],
                  "sum": 45.0,
                  "harBruktForventetInntektIStedetForArbeidsinntekt": false
                },
                "eps": {
                  "fradrag": [
                    {
                      "type": "Arbeidsinntekt",
                      "beløp": 20.0,
                      "utenlandskInntekt": null
                    },
                    {
                      "type": "Offentlig pensjon",
                      "beløp": 70.0,
                      "utenlandskInntekt": null
                    }
                  ],
                  "sum": 0.0
                }
              }
            }
        """.trimIndent()

        JSONAssert.assertEquals(expectedJson, objectMapper.writeValueAsString(beregning), true)
    }
}
