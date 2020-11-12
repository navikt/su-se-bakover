package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class EnsligStrategyTest {
    @Test
    fun `velger arbeidsinntekt dersom den er større enn forventet inntekt`() {
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
    fun `velger forventet inntekt dersom den er større enn arbeidsinntekt`() {
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
    fun `bruker bare fradrag som tilhører bruker`() {
        val arbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 5000.0, tilhører = FradragTilhører.EPS)
        val kontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 5000.0)
        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 15000.0)

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt)
        ).let { fradrag ->
            fradrag shouldBe listOf(kontantstøtte, forventetInntekt)
        }
    }
}
