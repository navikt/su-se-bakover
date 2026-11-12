package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.right
import common.presentation.beregning.FradragRequestJson
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.STANDARDAARTJUETJUEN
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype

internal class FradragRequestJsonToFradragTest {

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

    @Test
    fun `blokkerer Fradragstype Annet på nye beregninger`() {
        val json = FradragRequestJson(
            periode = PeriodeJson("2021-01-01", "2021-01-31"),
            type = Fradragstype.Kategori.Annet.name,
            beskrivelse = "vant på flaxlodd",
            beløp = 10.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER.toString(),
        )

        val result = json.toFradrag(januar(STANDARDAARTJUETJUEN))
        result.isLeft() shouldBe true
        result.leftOrNull()!!.httpCode.value shouldBe 400
    }
}
