package no.nav.su.se.bakover.database.beregning

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.fixedTidspunkt
import no.nav.su.se.bakover.database.persistertMånedsberegning
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class BeregningMapperTest {

    @Test
    fun `mapper fradrag til snapshot`() {
        assertFradragMapping(TestFradrag.toSnapshot(), TestFradrag)
    }

    @Test
    fun `mapper månedsberegning til snapshot`() {
        assertMånedsberegningMapping(TestMånedsberegning.toSnapshot(), TestMånedsberegning)
    }

    @Test
    fun `mapper beregning til snapshot`() {
        assertBeregningMapping(TestBeregning.toSnapshot(), TestBeregning)
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
                      "opprettet": "2021-01-01T01:02:03.456789Z",
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
                  },
                "fribeløpForEps": 0.0
                }
              ],
              "fradrag": [
                {
                  "opprettet": "2021-01-01T01:02:03.456789Z",
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
              "fradragStrategyName": "Enslig",
              "begrunnelse": null
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, objectMapper.writeValueAsString(TestBeregning.toSnapshot()), true)
    }

    @Test
    fun `should be equal to PersistertBeregning ignoring id, opprettet and begrunnelse`() {
        val a: Beregning = createBeregning(fixedTidspunkt, "a")
        val b: Beregning = createBeregning(fixedTidspunkt.plus(1, ChronoUnit.SECONDS), "b")
        a shouldBe b
        a.getId() shouldNotBe b.getId()
        a.getOpprettet() shouldNotBe b.getOpprettet()
        a.getBegrunnelse() shouldNotBe b.getBegrunnelse()
        (a === b) shouldBe false
    }

    private fun createBeregning(opprettet: Tidspunkt = fixedTidspunkt, begrunnelse: String = "begrunnelse") =
        PersistertBeregning(
            id = UUID.randomUUID(),
            opprettet = opprettet,
            sats = Sats.HØY,
            månedsberegninger = listOf(persistertMånedsberegning),
            fradrag = listOf(
                PersistertFradrag(
                    opprettet = fixedTidspunkt,
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 12000.0,
                    utenlandskInntekt = null,
                    periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            sumYtelse = 0,
            sumFradrag = 0.0,
            periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
            fradragStrategyName = FradragStrategyName.Enslig,
            begrunnelse = begrunnelse,
        )
}

internal fun assertFradragMapping(mapped: Fradrag, original: Fradrag) {
    mapped.periode shouldBe original.periode
    mapped.utenlandskInntekt shouldBe original.utenlandskInntekt
    mapped.månedsbeløp shouldBe original.månedsbeløp
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
    mapped.periode shouldBe original.periode
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
