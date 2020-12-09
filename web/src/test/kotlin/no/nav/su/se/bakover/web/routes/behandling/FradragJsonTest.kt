package no.nav.su.se.bakover.web.routes.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class FradragJsonTest {

    @Test
    fun `støtter fradrag input med periode`() {
        //language=json
        val fradragJson = """
        {
          "periode" : {
            "fraOgMed" : "2020-01-01",
            "tilOgMed" : "2020-01-31"
          },
          "type" : "Arbeidsinntekt",
          "beløp": 10.0,
          "utenlandskInntekt": null,
          "tilhører": "BRUKER"
        }
        """.trimIndent()

        deserialize<FradragJson>(fradragJson) shouldBe FradragJson(
            periode = PeriodeJson("2020-01-01", "2020-01-31"),
            type = Fradragstype.Arbeidsinntekt.toString(),
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString()
        )
    }

    @Test
    fun `støtter fradrag input uten periode`() {
        //language=json
        val fradragJson = """
        {
          "type" : "Arbeidsinntekt",
          "beløp": 10.0,
          "utenlandskInntekt": null,
          "tilhører": "BRUKER"
        }
        """.trimIndent()

        deserialize<FradragJson>(fradragJson) shouldBe FradragJson(
            periode = null,
            type = Fradragstype.Arbeidsinntekt.toString(),
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString()
        )
    }

    @Test
    fun `bruker innsendt periode ved konvertering til fradrag`() {
        val expected = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 10.0,
            periode = Periode(1.januar(2020), 31.januar(2020)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER
        )

        val json = FradragJson(
            periode = null,
            type = Fradragstype.Arbeidsinntekt.toString(),
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString()
        )

        json.toFradrag(Periode(1.januar(2020), 31.januar(2020))) shouldBe expected
    }
}
