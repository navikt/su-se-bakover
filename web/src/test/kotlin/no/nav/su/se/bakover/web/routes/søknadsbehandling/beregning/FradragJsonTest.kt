package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragskategori
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragskategoriWrapper
import no.nav.su.se.bakover.test.månedsperiodeJanuar2020
import no.nav.su.se.bakover.test.månedsperiodeJanuar2021
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
          "fradragskategoriWrapper" : {
            "kategori": "Arbeidsinntekt",
            "spesifisertkategori": null
          },
          "beløp": 10.0,
          "utenlandskInntekt": null,
          "tilhører": "BRUKER"
        }
        """.trimIndent()

        deserialize<FradragJson>(fradragJson) shouldBe FradragJson(
            periode = PeriodeJson("2020-01-01", "2020-01-31"),
            fradragskategoriWrapper = FradragskategoriWrapperJson(Fradragskategori.Arbeidsinntekt.toString()),
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
          "fradragskategoriWrapper": {
            "kategori": "Arbeidsinntekt",
            "spesifisertkategori": null
          },
          "beløp": 10.0,
          "utenlandskInntekt": null,
          "tilhører": "BRUKER"
        }
        """.trimIndent()

        deserialize<FradragJson>(fradragJson) shouldBe FradragJson(
            periode = null,
            fradragskategoriWrapper = FradragskategoriWrapperJson(Fradragskategori.Arbeidsinntekt.toString()),
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString(),
        )
    }

    @Test
    fun `fradrag som ikke har egen periode bruker den som sendes inn i mappingfunksjonen`() {
        val jsonUtenPeriode = FradragJson(
            periode = null,
            fradragskategoriWrapper = FradragskategoriWrapperJson(Fradragskategori.Arbeidsinntekt.toString()),
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString(),
        )

        val expectedPeriode = månedsperiodeJanuar2020
        val expected = FradragFactory.ny(
            type = FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt),
            månedsbeløp = 10.0,
            periode = expectedPeriode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        jsonUtenPeriode.toFradrag(expectedPeriode) shouldBe expected.right()
    }

    @Test
    fun `fradrag som har egen periode bruker benytter denne`() {
        val jsonUtenPeriode = FradragJson(
            periode = PeriodeJson("2021-01-01", "2021-01-31"),
            fradragskategoriWrapper = FradragskategoriWrapperJson(Fradragskategori.Arbeidsinntekt.toString()),
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString(),
        )

        val expected = FradragFactory.ny(
            type = FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt),
            månedsbeløp = 10.0,
            periode = månedsperiodeJanuar2021,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        jsonUtenPeriode.toFradrag(Periode.create(1.januar(2021), 31.desember(2021))) shouldBe expected.right()
    }
}
