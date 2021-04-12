package no.nav.su.se.bakover.web.routes.behandling.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class FradragJsonTest {

    @Test
    fun `deserialisering av fradragjson`() {
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
            tilhører = FradragTilhører.BRUKER.toString(),
        )
    }
}
