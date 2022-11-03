package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

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

    @Test
    fun `fradrag som ikke har egen periode bruker den som sendes inn i mappingfunksjonen`() {
        val jsonUtenPeriode = FradragRequestJson(
            periode = null,
            type = Fradragstype.Kategori.Arbeidsinntekt.toString(),
            beskrivelse = null,
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString(),
        )

        val expectedPeriode = januar(2020)
        val expected = FradragFactory.nyFradragsperiode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 10.0,
            periode = expectedPeriode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        jsonUtenPeriode.toFradrag(expectedPeriode) shouldBe expected.right()
    }

    @Test
    fun `fradrag som har egen periode bruker benytter denne`() {
        val jsonUtenPeriode = FradragRequestJson(
            periode = PeriodeJson("2021-01-01", "2021-01-31"),
            type = Fradragstype.Kategori.Arbeidsinntekt.toString(),
            beskrivelse = null,
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString(),
        )

        val expected = FradragFactory.nyFradragsperiode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 10.0,
            periode = januar(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        jsonUtenPeriode.toFradrag(år(2021)) shouldBe expected.right()
    }
}
