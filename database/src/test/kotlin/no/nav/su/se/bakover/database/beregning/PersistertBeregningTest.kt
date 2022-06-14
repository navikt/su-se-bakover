package no.nav.su.se.bakover.database.beregning

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class PersistertBeregningTest {

    @Test
    fun `serialiserer og derserialiserer beregning`() {
        val påDato = 21.mai(2021)
        val actualBeregning = createBeregning(
            opprettet = Tidspunkt.now(påDato.fixedClock()),
            periode = mai(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(påDato)),
        )
        //language=json
        val expectedJson = """
            {
              "id": "${actualBeregning.getId()}",
              "opprettet": "${actualBeregning.getOpprettet()}",
              "månedsberegninger": [
                {
                  "sumYtelse": 0,
                  "sumFradrag": 21989.126666666667,
                  "benyttetGrunnbeløp": 106399,
                  "sats": "HØY",
                  "satsbeløp": 21989.126666666667,
                  "fradrag": [
                    {
                      "fradragstype": "ForventetInntekt",
                      "beskrivelse": null,
                      "månedsbeløp": 55000.0,
                      "utenlandskInntekt": null,
                      "periode": {
                        "fraOgMed": "2021-05-01",
                        "tilOgMed": "2021-05-31"
                      },
                      "tilhører": "BRUKER"
                    },
                    {
                      "fradragstype": "Annet",
                      "beskrivelse": "vant på flaxlodd",
                      "månedsbeløp": 1000.0,
                      "utenlandskInntekt": null,
                      "periode": {
                        "fraOgMed": "2021-05-01",
                        "tilOgMed": "2021-05-31"
                      },
                      "tilhører": "BRUKER"
                    }
                  ],
                  "periode": {
                    "fraOgMed": "2021-05-01",
                    "tilOgMed": "2021-05-31"
                  },
                "fribeløpForEps": 0.0,
                "merknader": [
                    {
                      "type": "BeløpErNull"
                    }
                  ]
                }
              ],
              "fradrag": [
                {
                  "fradragstype": "ForventetInntekt",
                  "beskrivelse": null,
                  "månedsbeløp": 55000.0,
                  "utenlandskInntekt": null,
                  "periode": {
                    "fraOgMed": "2021-05-01",
                    "tilOgMed": "2021-05-31"
                  },
                  "tilhører": "BRUKER"
                },
                {
                  "fradragstype": "Annet",
                  "beskrivelse": "vant på flaxlodd",
                  "månedsbeløp": 1000.0,
                  "utenlandskInntekt": null,
                  "periode": {
                    "fraOgMed": "2021-05-01",
                    "tilOgMed": "2021-05-31"
                  },
                  "tilhører": "BRUKER"
                }
              ],
              "sumYtelse": 0,
              "sumFradrag": 21989.126666666667,
              "periode": {
                "fraOgMed": "2021-05-01",
                "tilOgMed": "2021-05-31"
              },
              "begrunnelse": "begrunnelse"
            }
        """.trimIndent()
        val actualJson: String = actualBeregning.serialiser()
        JSONAssert.assertEquals(expectedJson, actualJson, true)
        actualJson.deserialiserBeregning(satsFactoryTestPåDato(påDato)) shouldBe actualBeregning
    }

    @Test
    fun `serialisering av domenemodellen og den persisterte modellen er ikke lik`() {
        val beregning = createBeregning(strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato()))

        JSONAssert.assertNotEquals(
            objectMapper.writeValueAsString(beregning),
            beregning.serialiser(),
            true,
        )
    }

    @Test
    fun `should be equal to PersistertBeregning ignoring id, opprettet and begrunnelse`() {
        val a: Beregning =
            createBeregning(
                opprettet = fixedTidspunkt, begrunnelse = "a",
                strategy = BeregningStrategy.BorAlene(
                    satsFactoryTestPåDato(),
                ),
            )
        val b: Beregning =
            createBeregning(
                opprettet = fixedTidspunkt.plus(1, ChronoUnit.SECONDS),
                begrunnelse = "b",
                strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato()),
            )
        a shouldBe b
        a.getId() shouldNotBe b.getId()
        a.getOpprettet() shouldNotBe b.getOpprettet()
        a.getBegrunnelse() shouldNotBe b.getBegrunnelse()
        (a === b) shouldBe false
    }

    private fun createBeregning(
        periode: Periode = år(2021),
        opprettet: Tidspunkt = fixedTidspunkt,
        begrunnelse: String = "begrunnelse",
        strategy: BeregningStrategy,
    ) =
        BeregningFactory(clock = fixedClock).ny(
            id = UUID.randomUUID(),
            opprettet = opprettet,
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 55000.0,
                    utenlandskInntekt = null,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Annet("vant på flaxlodd"),
                    månedsbeløp = 1000.0,
                    utenlandskInntekt = null,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            begrunnelse = begrunnelse,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = strategy,
                ),
            ),
        )
}
