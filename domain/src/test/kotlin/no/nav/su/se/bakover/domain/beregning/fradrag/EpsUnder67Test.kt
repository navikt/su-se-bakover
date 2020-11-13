package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class EpsUnder67Test {
    @Test
    fun `velger arbeidsinntekt dersom den er større enn forventet inntekt`() {
        val arbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 25000.0)
        val kontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 5000.0)
        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 6000.0)

        FradragStrategy.EpsUnder67År.beregn(
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

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt)
        ).let { fradrag ->
            fradrag shouldBe listOf(kontantstøtte, forventetInntekt)
        }
    }

    @Test
    fun `tar med fradrag som tilhører EPS`() {
        val epsArbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 5000.0, tilhører = FradragTilhører.EPS)
        val kontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 5000.0)
        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 15000.0)

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(epsArbeidsinntekt, kontantstøtte, forventetInntekt)
        ).let { fradrag ->
            fradrag shouldBe listOf(epsArbeidsinntekt, kontantstøtte, forventetInntekt)
        }
    }
}
