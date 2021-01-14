package no.nav.su.se.bakover.domain.brev.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LagBrevinnholdForBeregningTest {

    @Test
    fun `Lagbrevinnhold for beregning med samme fradrag hele perioden`() {
        val beregning = BeregningFactory.ny(
            periode = Periode(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            fradragStrategy = FradragStrategy.Enslig
        )

        LagBrevinnholdForBeregning(beregning).brevInnhold shouldBe listOf(
            Beregningsperiode(
                periode = Periode(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                ytelsePerMåned = 19946,
                satsbeløpPerMåned = 20946,
                epsFribeløp = 0.0,
                fradrag = Fradrag(
                    bruker = FradragForBruker(
                        fradrag = listOf(
                            Månedsfradrag(
                                type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 1000.0,
                                utenlandskInntekt = null
                            )
                        ),
                        sum = 1000.0
                    ),
                    eps = FradragForEps(listOf(), 0.0)
                ),
            )
        )
    }

    @Test
    fun `Lagbrevinnhold for beregning med ulike perioder`() {
        val beregning = BeregningFactory.ny(
            periode = Periode(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 9999.0,
                    periode = Periode(fraOgMed = 1.juni(2020), tilOgMed = 31.august(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 2000.0,
                    periode = Periode(fraOgMed = 1.juni(2020), tilOgMed = 31.august(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            fradragStrategy = FradragStrategy.Enslig
        )

        LagBrevinnholdForBeregning(beregning).brevInnhold shouldBe listOf(
            Beregningsperiode(
                periode = Periode(fraOgMed = 1.mai(2020), tilOgMed = 31.mai(2020)),
                ytelsePerMåned = 19946,
                satsbeløpPerMåned = 20946,
                epsFribeløp = 0.0,
                fradrag = Fradrag(
                    bruker = FradragForBruker(
                        fradrag = listOf(
                            Månedsfradrag(
                                type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 1000.0,
                                utenlandskInntekt = null
                            )
                        ),
                        sum = 1000.0
                    ),
                    eps = FradragForEps(listOf(), 0.0)
                ),
            ),
            Beregningsperiode(
                periode = Periode(fraOgMed = 1.juni(2020), tilOgMed = 31.august(2020)),
                ytelsePerMåned = 8947,
                satsbeløpPerMåned = 20946,
                epsFribeløp = 0.0,
                fradrag = Fradrag(
                    bruker = FradragForBruker(
                        fradrag = listOf(
                            Månedsfradrag(
                                type = Fradragstype.Arbeidsinntekt.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 9999.0,
                                utenlandskInntekt = null
                            ),
                            Månedsfradrag(
                                type = Fradragstype.Sosialstønad.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 2000.0,
                                utenlandskInntekt = null
                            ),
                        ),
                        sum = 11999.0
                    ),
                    eps = FradragForEps(listOf(), 0.0)
                ),
            ),
            Beregningsperiode(
                periode = Periode(fraOgMed = 1.september(2020), tilOgMed = 30.april(2021)),
                ytelsePerMåned = 19946,
                satsbeløpPerMåned = 20946,
                epsFribeløp = 0.0,
                fradrag = Fradrag(
                    bruker = FradragForBruker(
                        fradrag = listOf(
                            Månedsfradrag(
                                type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 1000.0,
                                utenlandskInntekt = null
                            )
                        ),
                        sum = 1000.0
                    ),
                    eps = FradragForEps(listOf(), 0.0)
                ),
            ),
        )
    }

    @Test
    fun `lager brevinnhold for beregninger med fradrag for EPS`() {
        val beregning = BeregningFactory.ny(
            periode = Periode(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 20000.0,
                    periode = Periode(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                ),
                FradragFactory.ny(
                    type = Fradragstype.Kapitalinntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.desember(2020), tilOgMed = 31.desember(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                ),
            ),
            fradragStrategy = FradragStrategy.EpsOver67År
        )

        LagBrevinnholdForBeregning(beregning).brevInnhold shouldBe listOf(
            Beregningsperiode(
                periode = Periode(fraOgMed = 1.mai(2020), tilOgMed = 30.november(2020)),
                ytelsePerMåned = 15245,
                satsbeløpPerMåned = 20946,
                epsFribeløp = 15298.92,
                fradrag = Fradrag(
                    bruker = FradragForBruker(
                        fradrag = listOf(
                            Månedsfradrag(
                                type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 1000.0,
                                utenlandskInntekt = null
                            )
                        ),
                        sum = 1000.0
                    ),
                    eps = FradragForEps(
                        fradrag = listOf(
                            Månedsfradrag(
                                type = Fradragstype.Arbeidsinntekt.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 20000.0,
                                utenlandskInntekt = null
                            )
                        ),
                        sum = 20000.0
                    )
                ),
            ),
            Beregningsperiode(
                periode = Periode(fraOgMed = 1.desember(2020), tilOgMed = 31.desember(2020)),
                ytelsePerMåned = 14245,
                satsbeløpPerMåned = 20946,
                epsFribeløp = 15298.92,
                fradrag = Fradrag(
                    bruker = FradragForBruker(
                        fradrag = listOf(
                            Månedsfradrag(
                                type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 1000.0,
                                utenlandskInntekt = null
                            )
                        ),
                        sum = 1000.0
                    ),
                    eps = FradragForEps(
                        fradrag = listOf(
                            Månedsfradrag(
                                type = Fradragstype.Arbeidsinntekt.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 20000.0,
                                utenlandskInntekt = null
                            ),
                            Månedsfradrag(
                                type = Fradragstype.Kapitalinntekt.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 1000.0,
                                utenlandskInntekt = null
                            )
                        ),
                        sum = 21000.0
                    )
                ),
            ),
            Beregningsperiode(
                periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 30.april(2021)),
                ytelsePerMåned = 15245,
                satsbeløpPerMåned = 20946,
                epsFribeløp = 15298.92,
                fradrag = Fradrag(
                    bruker = FradragForBruker(
                        fradrag = listOf(
                            Månedsfradrag(
                                type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 1000.0,
                                utenlandskInntekt = null
                            )
                        ),
                        sum = 1000.0
                    ),
                    eps = FradragForEps(
                        fradrag = listOf(
                            Månedsfradrag(
                                type = Fradragstype.Arbeidsinntekt.toReadableTypeName(
                                    utenlandsk = false
                                ),
                                beløp = 20000.0,
                                utenlandskInntekt = null
                            )
                        ),
                        sum = 20000.0
                    )
                ),
            ),
        )
    }

    @Test
    fun `jsonformat på beregning stemmer overens med det som forventes av pdfgenerator`() {
        val beregning = Beregningsperiode(
            periode = Periode(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
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
                    sum = 45.0
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
              "periode": {
                "fraOgMed": "2020-05-01",
                "tilOgMed": "2021-04-30"
              },
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
                  "sum": 45.0
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
