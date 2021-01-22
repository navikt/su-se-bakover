package no.nav.su.se.bakover.web.routes.behandling.beregning

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
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
    fun `fradrag som ikke har egen periode bruker den som sendes inn i mappingfunksjonen`() {
        val jsonUtenPeriode = FradragJson(
            periode = null,
            type = Fradragstype.Arbeidsinntekt.toString(),
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString()
        )

        val expectedPeriode = Periode.create(1.januar(2020), 31.januar(2020))
        val expected = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 10.0,
            periode = expectedPeriode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER
        )

        jsonUtenPeriode.toFradrag(expectedPeriode) shouldBe expected.right()
    }

    @Test
    fun `fradrag som har egen periode bruker benytter denne`() {
        val jsonUtenPeriode = FradragJson(
            periode = PeriodeJson("2021-01-01", "2021-01-31"),
            type = Fradragstype.Arbeidsinntekt.toString(),
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString()
        )

        val expected = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 10.0,
            periode = Periode.create(1.januar(2021), 31.januar(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER
        )

        jsonUtenPeriode.toFradrag(Periode.create(1.januar(2021), 31.desember(2021))) shouldBe expected.right()
    }
}
