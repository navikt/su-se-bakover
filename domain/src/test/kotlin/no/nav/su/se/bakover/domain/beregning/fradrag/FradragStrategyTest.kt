package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class FradragStrategyTest {
    @Test
    fun `enslig velger arbeidsinntekt dersom den er større enn forventet inntekt`() {
        val arbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 25000.0)
        val kontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 5000.0)
        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 6000.0)

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt)
        ).let {
            it shouldBe listOf(arbeidsinntekt, kontantstøtte)
        }
    }

    @Test
    fun `enslig velger forventet inntekt dersom den er større enn arbeidsinntekt`() {
        val arbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 5000.0)
        val kontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 5000.0)
        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 15000.0)

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt)
        ).let { fradrag ->
            fradrag shouldBe listOf(kontantstøtte, forventetInntekt)
        }
    }

    @Test
    fun `enslig bruker bare fradrag som tilhører bruker`() {
        val arbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 5000.0, tilhører = FradragTilhører.EPS)
        val kontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 5000.0)
        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 15000.0)

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt)
        ).let { fradrag ->
            fradrag shouldBe listOf(kontantstøtte, forventetInntekt)
        }
    }

    @Test
    fun `alle beregninger av fradrag må inneholde brukers forventede inntekt etter uførhet`() {
        assertThrows<IllegalArgumentException> {
            FradragStrategy.Enslig.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsOver67År.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67År.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }
    }

    private fun lagFradrag(
        type: Fradragstype,
        beløp: Double,
        periode: Periode = Periode(1.januar(2020), 31.januar(2020)),
        tilhører: FradragTilhører = FradragTilhører.BRUKER
    ) = FradragFactory.ny(
        type = type,
        beløp = beløp,
        periode = periode,
        utenlandskInntekt = null,
        tilhører = tilhører
    )
}
