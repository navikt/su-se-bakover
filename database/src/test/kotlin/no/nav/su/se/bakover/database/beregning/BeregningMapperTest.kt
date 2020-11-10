package no.nav.su.se.bakover.database.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class BeregningMapperTest {

    private val periode = Periode(1.januar(2020), 31.januar(2020))
    private val fradrag = FradragFactory.ny(
        type = Fradragstype.Arbeidsinntekt,
        beløp = 12000.0,
        periode = periode,
        utenlandskInntekt = null
    )

    private val månedsberegning = MånedsberegningFactory.ny(
        periode = periode,
        sats = Sats.HØY,
        fradrag = listOf(fradrag)
    )

    private val beregning = BeregningFactory.ny(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        periode = periode,
        sats = Sats.HØY,
        fradrag = listOf(fradrag),
    )

    @Test
    fun `mapper fradrag til snapshot`() {
        fradrag.toSnapshot().let { assertFradragMapping(it, fradrag) }
    }

    @Test
    fun `mapper månedsberegning til snapshot`() {
        månedsberegning.toSnapshot().let { assertMånedsberegningMapping(it, månedsberegning) }
    }

    @Test
    fun `mapper beregning til snapshot`() {
        beregning.toSnapshot().let { assertBeregningMapping(it, beregning) }
    }

    @Test
    fun `mapper snapshot av beregning til json`() {
        //language=json
        val expectedJson = """
            {
              "id": "${beregning.getId()}",
              "opprettet": "${beregning.getOpprettet()}",
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
                      "fradragstype": "Arbeidsinntekt",
                      "totaltFradrag": 12000.0,
                      "utenlandskInntekt": null,
                      "fradragPerMåned": 12000.0,
                      "periode": {
                        "fraOgMed": "2020-01-01",
                        "tilOgMed": "2020-01-31",
                        "antallMåneder": 1
                      }
                    }
                  ],
                  "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-01-31",
                    "antallMåneder": 1
                  }
                }
              ],
              "fradrag": [
                {
                  "fradragstype": "Arbeidsinntekt",
                  "totaltFradrag": 12000.0,
                  "utenlandskInntekt": null,
                  "fradragPerMåned": 12000.0,
                  "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-01-31",
                    "antallMåneder": 1
                  }
                }
              ],
              "sumYtelse": 8637,
              "sumFradrag": 12000.0,
              "sumYtelseErUnderMinstebeløp": false,
              "periode": {
                "fraOgMed": "2020-01-01",
                "tilOgMed": "2020-01-31",
                "antallMåneder": 1
              }
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, objectMapper.writeValueAsString(beregning.toSnapshot()), true)
    }
}

internal fun assertFradragMapping(mapped: Fradrag, original: Fradrag) {
    mapped.getPeriode() shouldBe original.getPeriode()
    mapped.getUtenlandskInntekt() shouldBe original.getUtenlandskInntekt()
    mapped.getTotaltFradrag() shouldBe original.getTotaltFradrag()
    mapped.getFradragPerMåned() shouldBe original.getFradragPerMåned()
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
    mapped.getSumYtelseErUnderMinstebeløp() shouldBe original.getSumYtelseErUnderMinstebeløp()
    mapped.getFradrag().forEachIndexed { index, fradrag ->
        assertFradragMapping(fradrag, original.getFradrag()[index])
    }
    mapped.getMånedsberegninger().forEachIndexed { index, månedsberegning ->
        assertMånedsberegningMapping(månedsberegning, original.getMånedsberegninger()[index])
    }
}
