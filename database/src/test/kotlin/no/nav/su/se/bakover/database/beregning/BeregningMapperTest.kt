package no.nav.su.se.bakover.database.beregning

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class BeregningMapperTest {

    @Test
    fun `mapper fradrag til snapshot`() {
        createBeregning(strategy = BeregningStrategy.BorAlene).let {
            assertFradragMapping(it.toSnapshot().getFradrag(), it.getFradrag())
        }
    }

    @Test
    fun `mapper månedsberegning til snapshot`() {
        createBeregning(strategy = BeregningStrategy.BorAlene).let {
            assertMånedsberegningMapping(it.toSnapshot().getMånedsberegninger(), it.getMånedsberegninger())
        }
    }

    @Test
    fun `mapper beregning til snapshot`() {
        createBeregning(strategy = BeregningStrategy.BorAlene).let {
            assertBeregningMapping(it.toSnapshot(), it)
        }
    }

    @Test
    fun `mapper snapshot av beregning til json`() {
        val beregningSnapshot = createBeregning(periode = mai(2021), strategy = BeregningStrategy.BorAlene)
        //language=json
        val expectedJson = """
            {
              "id": "${beregningSnapshot.getId()}",
              "opprettet": "${beregningSnapshot.getOpprettet()}",
              "sats": "HØY",
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
                      "månedsbeløp": 55000.0,
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
                  "månedsbeløp": 55000.0,
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
              "fradragStrategyName": "Enslig",
              "begrunnelse": "begrunnelse"
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, serialiserBeregning(beregningSnapshot), true)
    }

    @Test
    fun `serialisering av snapshot og rå beregning er ikke lik`() {
        val beregning = createBeregning(strategy = BeregningStrategy.BorAlene)

        JSONAssert.assertNotEquals(
            objectMapper.writeValueAsString(beregning),
            serialiserBeregning(beregning),
            true,
        )
    }

    @Test
    fun `snapshot er idempotent`() {
        val original: Beregning = createBeregning(strategy = BeregningStrategy.BorAlene)
        val snapshot: PersistertBeregning = original.toSnapshot()
        val idempotent: PersistertBeregning = snapshot.toSnapshot()

        snapshot shouldBe idempotent

        JSONAssert.assertEquals(serialiserBeregning(original), serialiserBeregning(snapshot), true)
        JSONAssert.assertEquals(serialiserBeregning(snapshot), serialiserBeregning(idempotent), true)
        JSONAssert.assertEquals(serialiserBeregning(original), serialiserBeregning(idempotent), true)
    }

    @Test
    fun `should be equal to PersistertBeregning ignoring id, opprettet and begrunnelse`() {
        val a: Beregning = createBeregning(opprettet = fixedTidspunkt, begrunnelse = "a", strategy = BeregningStrategy.BorAlene).toSnapshot()
        val b: Beregning =
            createBeregning(opprettet = fixedTidspunkt.plus(1, ChronoUnit.SECONDS), begrunnelse = "b", strategy = BeregningStrategy.BorAlene).toSnapshot()
        a shouldBe b
        a.getId() shouldNotBe b.getId()
        a.getOpprettet() shouldNotBe b.getOpprettet()
        a.getBegrunnelse() shouldNotBe b.getBegrunnelse()
        (a === b) shouldBe false
    }

    private fun createBeregning(
        periode: Periode = periode2021,
        opprettet: Tidspunkt = fixedTidspunkt,
        begrunnelse: String = "begrunnelse",
        strategy: BeregningStrategy,
    ) =
        BeregningFactory(clock = fixedClock).ny(
            id = UUID.randomUUID(),
            opprettet = opprettet,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 55000.0,
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

internal fun assertFradragMapping(mapped: List<Fradrag>, original: List<Fradrag>) {
    mapped.forEachIndexed { index, fradrag ->
        fradrag.periode shouldBe original[index].periode
        fradrag.utenlandskInntekt shouldBe original[index].utenlandskInntekt
        fradrag.månedsbeløp shouldBe original[index].månedsbeløp
    }
}

internal fun assertMånedsberegningMapping(mapped: List<Månedsberegning>, original: List<Månedsberegning>) {
    mapped.forEachIndexed { index, månedsberegning ->
        månedsberegning.getBenyttetGrunnbeløp() shouldBe original[index].getBenyttetGrunnbeløp()
        månedsberegning.getSatsbeløp() shouldBe original[index].getSatsbeløp()
        månedsberegning.getSats() shouldBe original[index].getSats()
        månedsberegning.getSumYtelse() shouldBe original[index].getSumYtelse()
        månedsberegning.getSumFradrag() shouldBe original[index].getSumFradrag()
        assertFradragMapping(månedsberegning.getFradrag(), original[index].getFradrag())
    }
}

internal fun assertBeregningMapping(mapped: Beregning, original: Beregning) {
    mapped.getId() shouldBe original.getId()
    mapped.getOpprettet() shouldBe original.getOpprettet()
    mapped.periode shouldBe original.periode
    mapped.getSats() shouldBe original.getSats()
    mapped.getSumFradrag() shouldBe original.getSumFradrag()
    mapped.getSumYtelse() shouldBe original.getSumYtelse()
    assertFradragMapping(mapped.getFradrag(), original.getFradrag())
    assertMånedsberegningMapping(mapped.getMånedsberegninger(), original.getMånedsberegninger())
}
