package no.nav.su.se.bakover.database.beregning

import beregning.domain.Beregning
import beregning.domain.BeregningFactory
import beregning.domain.BeregningStrategy
import beregning.domain.Beregningsperiode
import io.kotest.assertions.json.ArrayOrder
import io.kotest.assertions.json.FieldComparison
import io.kotest.assertions.json.NumberFormat
import io.kotest.assertions.json.PropertyOrder
import io.kotest.assertions.json.TypeCoercion
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.fixedClock
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class PersistertBeregningTest {

    @Test
    fun `serialiserer og derserialiserer beregning`() {
        val påDato = 21.mai(2021)
        val actualBeregning = createBeregning(
            opprettet = Tidspunkt.now(påDato.fixedClock()),
            periode = mai(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(påDato), Sakstype.UFØRE),
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
        actualJson.shouldEqualJson {
            propertyOrder = PropertyOrder.Strict
            arrayOrder = ArrayOrder.Strict
            fieldComparison = FieldComparison.Strict
            numberFormat = NumberFormat.Strict
            typeCoercion = TypeCoercion.Disabled
            expectedJson
        }
        actualJson.deserialiserBeregning(satsFactoryTest, Sakstype.UFØRE, saksnummer, false) shouldBe actualBeregning
    }

    @Test
    fun `serialisering av domenemodellen og den persisterte modellen er ikke lik`() {
        val beregning = createBeregning(strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE))

        JSONAssert.assertNotEquals(
            serialize(beregning),
            beregning.serialiser(),
            true,
        )
    }

    @Test
    fun `should be equal to PersistertBeregning ignoring id, opprettet and begrunnelse`() {
        val a: Beregning =
            createBeregning(
                opprettet = fixedTidspunkt,
                begrunnelse = "a",
                strategy = BeregningStrategy.BorAlene(
                    satsFactory = satsFactoryTestPåDato(),
                    sakstype = Sakstype.UFØRE,
                ),
            )
        val b: Beregning =
            createBeregning(
                opprettet = fixedTidspunkt.plus(1, ChronoUnit.SECONDS),
                begrunnelse = "b",
                strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
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
