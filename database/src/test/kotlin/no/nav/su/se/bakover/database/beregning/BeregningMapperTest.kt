package no.nav.su.se.bakover.database.beregning

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class BeregningMapperTest {

    @Test
    fun `mapper fradrag til snapshot`() {
        TestFradrag.toSnapshot().let { assertFradragMapping(it, TestFradrag) }
    }

    @Test
    fun `mapper månedsberegning til snapshot`() {
        TestMånedsberegning.toSnapshot().let { assertMånedsberegningMapping(it, TestMånedsberegning) }
    }

    @Test
    fun `mapper beregning til snapshot`() {
        TestBeregning.toSnapshot().let { assertBeregningMapping(it, TestBeregning) }
    }

    @Test
    fun `mapper snapshot av beregning til json`() {
        //language=json
        val expectedJson = """
            {
              "id": "${TestBeregning.getId()}",
              "opprettet": "${TestBeregning.getOpprettet()}",
              "sats": "HØY",
              "månedsberegninger": [
                {
                  "sumYtelse": 8637,
                  "sumFradrag": 12000.0,
                  "benyttetGrunnbeløp": 99858,
                  "sats": "HØY",
                  "satsbeløp": 20637.32,
                  "fradrag": [
                    {
                      "fradragstype": "ForventetInntekt",
                      "månedsbeløp": 12000.0,
                      "utenlandskInntekt": null,
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      },
                      "tilhører": "BRUKER"
                    }
                  ],
                  "periode": {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-01-31"
                  }
                }
              ],
              "fradrag": [
                {
                  "fradragstype": "ForventetInntekt",
                  "månedsbeløp": 12000.0,
                  "utenlandskInntekt": null,
                  "periode": {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-01-31"
                  },
                  "tilhører": "BRUKER"
                }
              ],
              "sumYtelse": 8637,
              "sumFradrag": 12000.0,
              "periode": {
                "fraOgMed": "2021-01-01",
                "tilOgMed": "2021-01-31"
              },
              "fradragStrategyName": "Enslig"
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, objectMapper.writeValueAsString(TestBeregning.toSnapshot()), true)
    }

    @Test
    fun `kan deserialisere fradrag ved hjelp av alias for månedsbeløp`() {
        //language=json
        val json = """
            {
                  "fradragstype": "ForventetInntekt",
                  "totaltFradrag": 12000.0,
                  "utenlandskInntekt": null,
                  "periode": {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-01-31"
                  },
                  "tilhører": "BRUKER"
                }
        """.trimIndent()

        objectMapper.readValue<PersistertFradrag>(json) shouldBe PersistertFradrag(
            fradragstype = Fradragstype.ForventetInntekt,
            månedsbeløp = 12000.0,
            utenlandskInntekt = null,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            tilhører = FradragTilhører.BRUKER
        )
    }
}

internal fun assertFradragMapping(mapped: Fradrag, original: Fradrag) {
    mapped.getPeriode() shouldBe original.getPeriode()
    mapped.getUtenlandskInntekt() shouldBe original.getUtenlandskInntekt()
    mapped.getMånedsbeløp() shouldBe original.getMånedsbeløp()
}

internal fun assertMånedsberegningMapping(mapped: Månedsberegning, original: Månedsberegning) {
    mapped.getBenyttetGrunnbeløp() shouldBe original.getBenyttetGrunnbeløp()
    mapped.getSatsbeløp() shouldBe original.getSatsbeløp()
    mapped.getSats() shouldBe original.getSats()
    mapped.getSumYtelse() shouldBe original.getSumYtelse()
    mapped.getSumFradrag() shouldBe original.getSumFradrag()
    mapped.getFradrag().forEachIndexed { index, fradrag ->
        assertFradragMapping(fradrag, original.getFradrag()[index])
    }
}

internal fun assertBeregningMapping(mapped: Beregning, original: Beregning) {
    mapped.getId() shouldBe original.getId()
    mapped.getOpprettet() shouldBe original.getOpprettet()
    mapped.getPeriode() shouldBe original.getPeriode()
    mapped.getSats() shouldBe original.getSats()
    mapped.getSumFradrag() shouldBe original.getSumFradrag()
    mapped.getSumYtelse() shouldBe original.getSumYtelse()
    mapped.getFradrag().forEachIndexed { index, fradrag ->
        assertFradragMapping(fradrag, original.getFradrag()[index])
    }
    mapped.getMånedsberegninger().forEachIndexed { index, månedsberegning ->
        assertMånedsberegningMapping(månedsberegning, original.getMånedsberegninger()[index])
    }
}
