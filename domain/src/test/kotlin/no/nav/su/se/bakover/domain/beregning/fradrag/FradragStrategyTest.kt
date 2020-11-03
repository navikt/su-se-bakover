package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import org.junit.jupiter.api.Test

internal class FradragStrategyTest {
    @Test
    fun `enslig velger arbeidsinntekt dersom den er større enn forventet inntekt`() {
        val arbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 25000)
        val kontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 5000)

        FradragStrategy.Enslig.beregnFradrag(
            forventetInntekt = 6000,
            fradrag = listOf(arbeidsinntekt, kontantstøtte),
        ).let {
            it shouldBe listOf(arbeidsinntekt, kontantstøtte)
        }
    }

    @Test
    fun `enslig velger forventet inntekt dersom den er større enn arbeidsinntekt`() {
        val arbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 5000)
        val kontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 5000)

        FradragStrategy.Enslig.beregnFradrag(
            forventetInntekt = 15000,
            fradrag = listOf(arbeidsinntekt, kontantstøtte),
        ).let { fradrag ->
            fradrag shouldHaveSize 2
            val (forventetInntekt, kontant) = fradrag.partition { it.type == Fradragstype.ForventetInntekt }
            kontant shouldBe listOf(kontantstøtte)
            forventetInntekt.first().let {
                it.type shouldBe Fradragstype.ForventetInntekt
                it.beløp shouldBe 15000
            }
        }
    }

    private fun lagFradrag(type: Fradragstype, beløp: Int): Fradrag = Fradrag(
        type = type,
        beløp = beløp,
        utenlandskInntekt = null
    )
}
