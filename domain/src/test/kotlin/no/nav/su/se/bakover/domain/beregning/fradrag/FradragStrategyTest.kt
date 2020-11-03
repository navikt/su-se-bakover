package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test

internal class FradragStrategyTest {
    @Test
    fun `enslig velger arbeidsinntekt dersom den er større enn forventet inntekt`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
        val arbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 25000.0, periode)
        val kontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 5000.0, periode)

        FradragStrategy.Enslig.beregnFradrag(
            forventetInntekt = 6000,
            fradrag = listOf(arbeidsinntekt, kontantstøtte),
            periode = periode
        ).let {
            it shouldBe listOf(arbeidsinntekt, kontantstøtte)
        }
    }

    @Test
    fun `enslig velger forventet inntekt dersom den er større enn arbeidsinntekt`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
        val arbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 5000.0, periode)
        val kontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 5000.0, periode)

        FradragStrategy.Enslig.beregnFradrag(
            forventetInntekt = 15000,
            fradrag = listOf(arbeidsinntekt, kontantstøtte),
            periode = periode
        ).let { fradrag ->
            fradrag shouldHaveSize 2
            val (forventetInntekt, kontant) = fradrag.partition { it.type() == Fradragstype.ForventetInntekt }
            kontant shouldBe listOf(kontantstøtte)
            forventetInntekt.first().let {
                it.type() shouldBe Fradragstype.ForventetInntekt
                it.totalBeløp() shouldBe 15000
            }
        }
    }

    private fun lagFradrag(type: Fradragstype, beløp: Double, periode: Periode) = FradragFactory.ny(
        type = type,
        beløp = beløp,
        utenlandskInntekt = null,
        periode = periode
    )
}
