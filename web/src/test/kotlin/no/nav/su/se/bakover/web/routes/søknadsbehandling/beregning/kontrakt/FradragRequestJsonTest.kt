package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.kontrakt

import common.presentation.beregning.FradragRequestJson
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.web.Frontendkontrakt
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype

@Frontendkontrakt
internal class FradragRequestJsonTest {

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

        deserialize<FradragRequestJson>(fradragJson) shouldBe FradragRequestJson(
            periode = PeriodeJson("2020-01-01", "2020-01-31"),
            type = Fradragstype.Kategori.Arbeidsinntekt.toString(),
            beskrivelse = null,
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString(),
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

        deserialize<FradragRequestJson>(fradragJson) shouldBe FradragRequestJson(
            periode = null,
            type = Fradragstype.Kategori.Arbeidsinntekt.toString(),
            beskrivelse = null,
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString(),
        )
    }
}
