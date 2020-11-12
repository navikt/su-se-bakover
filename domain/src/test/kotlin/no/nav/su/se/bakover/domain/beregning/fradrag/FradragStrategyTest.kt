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
            val (forventetInntekt, kontant) = fradrag.partition { it.getFradragstype() == Fradragstype.ForventetInntekt }
            kontant shouldBe listOf(kontantstøtte)
            forventetInntekt.first().let {
                it.getFradragstype() shouldBe Fradragstype.ForventetInntekt
                it.getTotaltFradrag() shouldBe 15000
            }
        }
    }

    @Test
    fun `enslig legger til fradrag for forventet inntekt etter uførhet dersom det ikke eksisterer fradrag for arbeidsinntekt`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
        FradragStrategy.Enslig.beregnFradrag(
            forventetInntekt = 10000,
            fradrag = listOf(),
            periode = periode
        ).let { fradrag ->
            fradrag shouldHaveSize 1
            fradrag.single() { it.getFradragstype() == Fradragstype.ForventetInntekt }
            fradrag.first().getTotaltFradrag() shouldBe 10000
        }
    }

    @Test
    fun `enslig bruker bare fradrag som tilhører bruker`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
        val arbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 5000.0, periode, tilhører = FradragTilhører.EPS)
        val kontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 5000.0, periode)

        FradragStrategy.Enslig.beregnFradrag(
            forventetInntekt = 15000,
            fradrag = listOf(arbeidsinntekt, kontantstøtte),
            periode = periode
        ).let { fradrag ->
            fradrag shouldHaveSize 2
            fradrag.none { it.getFradragstype() == Fradragstype.Arbeidsinntekt }
        }
    }

    private fun lagFradrag(
        type: Fradragstype,
        beløp: Double,
        periode: Periode,
        tilhører: FradragTilhører = FradragTilhører.BRUKER
    ) = FradragFactory.ny(
        type = type,
        beløp = beløp,
        periode = periode,
        utenlandskInntekt = null,
        tilhører = tilhører
    )
}
