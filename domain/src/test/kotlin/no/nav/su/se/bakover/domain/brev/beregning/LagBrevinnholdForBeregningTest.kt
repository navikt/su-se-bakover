package no.nav.su.se.bakover.domain.brev.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LagBrevinnholdForBeregningTest {

    @Test
    fun `Lagbrevinnhold for beregning med samme fradrag hele perioden`() {
        val periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021))
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                no.nav.su.se.bakover.domain.beregning.Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato()),
                ),
            ),
        )

        LagBrevinnholdForBeregning(beregning).brevInnhold shouldBe listOf(
            Beregningsperiode(
                periode = BrevPeriode(fraOgMed = "mai 2020", tilOgMed = "april 2021"),
                ytelsePerMåned = 19946,
                satsbeløpPerMåned = 20946,
                epsFribeløp = 0,
                fradrag = Fradrag(
                    bruker = listOf(
                        Månedsfradrag(
                            type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                utenlandsk = false,
                            ),
                            beløp = 1000,
                            utenlandskInntekt = null,
                        ),
                    ),
                    eps = Fradrag.Eps(emptyList(), false),
                ),
                sats = "høy",
            ),
        )
    }

    @Test
    fun `Lagbrevinnhold for beregning med ulike perioder`() {
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 9999.0,
                    periode = Periode.create(fraOgMed = 1.juni(2020), tilOgMed = 31.august(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 2000.0,
                    periode = Periode.create(fraOgMed = 1.juni(2020), tilOgMed = 31.august(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                no.nav.su.se.bakover.domain.beregning.Beregningsperiode(
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato()),
                ),
            ),
        )

        LagBrevinnholdForBeregning(beregning).brevInnhold shouldBe listOf(
            Beregningsperiode(
                periode = BrevPeriode(fraOgMed = "mai 2020", tilOgMed = "mai 2020"),
                ytelsePerMåned = 19946,
                satsbeløpPerMåned = 20946,
                epsFribeløp = 0,
                fradrag = Fradrag(
                    bruker = listOf(
                        Månedsfradrag(
                            type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                utenlandsk = false,
                            ),
                            beløp = 1000,
                            utenlandskInntekt = null,
                        ),
                    ),
                    eps = Fradrag.Eps(emptyList(), false),
                ),
                sats = "høy",
            ),
            Beregningsperiode(
                periode = BrevPeriode(fraOgMed = "juni 2020", tilOgMed = "august 2020"),
                ytelsePerMåned = 8947,
                satsbeløpPerMåned = 20946,
                epsFribeløp = 0,
                fradrag = Fradrag(
                    bruker = listOf(
                        Månedsfradrag(
                            type = Fradragstype.Arbeidsinntekt.toReadableTypeName(
                                utenlandsk = false,
                            ),
                            beløp = 9999,
                            utenlandskInntekt = null,
                        ),
                        Månedsfradrag(
                            type = Fradragstype.Sosialstønad.toReadableTypeName(
                                utenlandsk = false,
                            ),
                            beløp = 2000,
                            utenlandskInntekt = null,
                        ),
                    ),
                    eps = Fradrag.Eps(emptyList(), false),
                ),
                sats = "høy",
            ),
            Beregningsperiode(
                periode = BrevPeriode(fraOgMed = "september 2020", tilOgMed = "april 2021"),
                ytelsePerMåned = 19946,
                satsbeløpPerMåned = 20946,
                epsFribeløp = 0,
                fradrag = Fradrag(
                    bruker = listOf(
                        Månedsfradrag(
                            type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                utenlandsk = false,
                            ),
                            beløp = 1000,
                            utenlandskInntekt = null,
                        ),
                    ),
                    eps = Fradrag.Eps(emptyList(), false),
                ),
                sats = "høy",
            ),
        )
    }

    @Test
    fun `lager brevinnhold for beregninger med fradrag for EPS`() {
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 20000.0,
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Kapitalinntekt,
                    månedsbeløp = 1000.0,
                    periode = desember(2020),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
            beregningsperioder = listOf(
                no.nav.su.se.bakover.domain.beregning.Beregningsperiode(
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    strategy = BeregningStrategy.Eps67EllerEldre(satsFactoryTestPåDato()),

                ),
            ),
        )

        LagBrevinnholdForBeregning(beregning).brevInnhold shouldBe listOf(
            Beregningsperiode(
                periode = BrevPeriode(fraOgMed = "mai 2020", tilOgMed = "november 2020"),
                ytelsePerMåned = 13067,
                satsbeløpPerMåned = 19257,
                epsFribeløp = 14810,
                fradrag = Fradrag(
                    bruker = listOf(
                        Månedsfradrag(
                            type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                utenlandsk = false,
                            ),
                            beløp = 1000,
                            utenlandskInntekt = null,
                        ),
                    ),
                    eps = Fradrag.Eps(
                        listOf(
                            Månedsfradrag(
                                type = Fradragstype.Arbeidsinntekt.toReadableTypeName(
                                    utenlandsk = false,
                                ),
                                beløp = 20000,
                                utenlandskInntekt = null,
                            ),
                        ),
                        false,
                    ),
                ),
                sats = "ordinær",
            ),
            Beregningsperiode(
                periode = BrevPeriode(fraOgMed = "desember 2020", tilOgMed = "desember 2020"),
                ytelsePerMåned = 12067,
                satsbeløpPerMåned = 19257,
                epsFribeløp = 14810,
                fradrag = Fradrag(
                    bruker = listOf(
                        Månedsfradrag(
                            type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                utenlandsk = false,
                            ),
                            beløp = 1000,
                            utenlandskInntekt = null,
                        ),
                    ),
                    eps = Fradrag.Eps(
                        listOf(
                            Månedsfradrag(
                                type = Fradragstype.Arbeidsinntekt.toReadableTypeName(
                                    utenlandsk = false,
                                ),
                                beløp = 20000,
                                utenlandskInntekt = null,
                            ),
                            Månedsfradrag(
                                type = Fradragstype.Kapitalinntekt.toReadableTypeName(
                                    utenlandsk = false,
                                ),
                                beløp = 1000,
                                utenlandskInntekt = null,
                            ),
                        ),
                        false,
                    ),
                ),
                sats = "ordinær",
            ),
            Beregningsperiode(
                periode = BrevPeriode(fraOgMed = "januar 2021", tilOgMed = "april 2021"),
                ytelsePerMåned = 13067,
                satsbeløpPerMåned = 19257,
                epsFribeløp = 14810,
                fradrag = Fradrag(
                    bruker = listOf(
                        Månedsfradrag(
                            type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                utenlandsk = false,
                            ),
                            beløp = 1000,
                            utenlandskInntekt = null,
                        ),
                    ),
                    eps = Fradrag.Eps(
                        listOf(
                            Månedsfradrag(
                                type = Fradragstype.Arbeidsinntekt.toReadableTypeName(
                                    utenlandsk = false,
                                ),
                                beløp = 20000,
                                utenlandskInntekt = null,
                            ),
                        ),
                        false,
                    ),
                ),
                sats = "ordinær",
            ),
        )
    }

    @Test
    fun `jsonformat på beregning stemmer overens med det som forventes av pdfgenerator`() {
        val beregning = Beregningsperiode(
            periode = BrevPeriode(fraOgMed = "mai 2020", tilOgMed = "april 2021"),
            ytelsePerMåned = 100,
            satsbeløpPerMåned = 100,
            epsFribeløp = 100,
            fradrag = Fradrag(
                bruker = listOf(
                    Månedsfradrag(
                        type = Fradragstype.Arbeidsinntekt.toReadableTypeName(utenlandsk = false),
                        beløp = 10,
                        utenlandskInntekt = null,
                    ),
                    Månedsfradrag(
                        type = Fradragstype.OffentligPensjon.toReadableTypeName(utenlandsk = false),
                        beløp = 35,
                        utenlandskInntekt = null,
                    ),
                    Månedsfradrag(
                        type = Fradragstype.OffentligPensjon.toReadableTypeName(utenlandsk = true),
                        beløp = 70,
                        utenlandskInntekt = UtenlandskInntekt.create(
                            beløpIUtenlandskValuta = 140,
                            valuta = "SEK",
                            kurs = 0.5,
                        ),
                    ),
                ),
                eps = Fradrag.Eps(
                    listOf(
                        Månedsfradrag(
                            type = Fradragstype.Arbeidsinntekt.toReadableTypeName(utenlandsk = false),
                            beløp = 20,
                            utenlandskInntekt = null,
                        ),
                        Månedsfradrag(
                            type = Fradragstype.OffentligPensjon.toReadableTypeName(utenlandsk = false),
                            beløp = 70,
                            utenlandskInntekt = null,
                        ),
                    ),
                    false,
                ),
            ),
            sats = "høy",
        )

        //language=json
        val expectedJson = """
            {
              "periode": {
                "fraOgMed": "mai 2020",
                "tilOgMed": "april 2021"
              },
              "ytelsePerMåned": 100,
              "satsbeløpPerMåned": 100,
              "epsFribeløp": 100.0,
              "fradrag": {
                "bruker": [
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
                  "harFradragMedSumSomErLavereEnnFribeløp": false
                }
            },
            "sats": "høy"
          }
        """.trimIndent()

        JSONAssert.assertEquals(expectedJson, objectMapper.writeValueAsString(beregning), true)
    }

    @Test
    fun `Fradrag for eps er tom liste hvis beløp er lavere enn fribeløp`() {
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Introduksjonsstønad,
                    månedsbeløp = 3000.0,
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
            beregningsperioder = listOf(
                no.nav.su.se.bakover.domain.beregning.Beregningsperiode(
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    strategy = BeregningStrategy.Eps67EllerEldre(satsFactoryTestPåDato()),
                )
            ),

        )

        LagBrevinnholdForBeregning(beregning).brevInnhold shouldBe listOf(
            Beregningsperiode(
                periode = BrevPeriode(fraOgMed = "mai 2020", tilOgMed = "april 2021"),
                ytelsePerMåned = 18257,
                satsbeløpPerMåned = 19257,
                epsFribeløp = 14810,
                fradrag = Fradrag(
                    bruker = listOf(
                        Månedsfradrag(
                            type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                utenlandsk = false,
                            ),
                            beløp = 1000,
                            utenlandskInntekt = null,
                        ),
                    ),
                    eps = Fradrag.Eps(
                        emptyList(),
                        true,
                    ),
                ),
                sats = "ordinær",
            ),
        )
    }

    @Test
    fun `Tar med alle fradrag for eps hvis sum av fradragene er høyere enn fribeløp`() {
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Kontantstøtte,
                    månedsbeløp = 6000.0,
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Introduksjonsstønad,
                    månedsbeløp = 6000.0,
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.BidragEtterEkteskapsloven,
                    månedsbeløp = 6000.0,
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
            beregningsperioder = listOf(
                no.nav.su.se.bakover.domain.beregning.Beregningsperiode(
                    periode = Periode.create(fraOgMed = 1.mai(2020), tilOgMed = 30.april(2021)),
                    strategy = BeregningStrategy.Eps67EllerEldre(satsFactoryTestPåDato()),
                ),
            ),
        )

        LagBrevinnholdForBeregning(beregning).brevInnhold shouldBe listOf(
            Beregningsperiode(
                periode = BrevPeriode(fraOgMed = "mai 2020", tilOgMed = "april 2021"),
                ytelsePerMåned = 15067,
                satsbeløpPerMåned = 19257,
                epsFribeløp = 14810,
                fradrag = Fradrag(
                    bruker = listOf(
                        Månedsfradrag(
                            type = Fradragstype.ForventetInntekt.toReadableTypeName(
                                utenlandsk = false,
                            ),
                            beløp = 1000,
                            utenlandskInntekt = null,
                        ),
                    ),
                    eps = Fradrag.Eps(
                        listOf(
                            Månedsfradrag(
                                type = Fradragstype.BidragEtterEkteskapsloven.toReadableTypeName(
                                    utenlandsk = false,
                                ),
                                beløp = 6000,
                                utenlandskInntekt = null,
                            ),
                            Månedsfradrag(
                                type = Fradragstype.Introduksjonsstønad.toReadableTypeName(
                                    utenlandsk = false,
                                ),
                                beløp = 6000,
                                utenlandskInntekt = null,
                            ),
                            Månedsfradrag(
                                type = Fradragstype.Kontantstøtte.toReadableTypeName(
                                    utenlandsk = false,
                                ),
                                beløp = 6000,
                                utenlandskInntekt = null,
                            ),
                        ),
                        false,
                    ),
                ),
                sats = "ordinær",
            ),
        )
    }
}
