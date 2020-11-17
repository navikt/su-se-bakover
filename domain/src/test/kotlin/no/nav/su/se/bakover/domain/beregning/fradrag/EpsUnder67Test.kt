package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test

internal class EpsUnder67Test {
    @Test
    fun `velger brukers arbeidsinntekt dersom den er større enn forventet inntekt`() {
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
    fun `velger brukers forventet inntekt dersom den er større enn arbeidsinntekt`() {
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

        val expectedEpsInntekt = lagFradrag(Fradragstype.BeregnetFradragEPS, 5000.0, tilhører = FradragTilhører.EPS)

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(epsArbeidsinntekt, kontantstøtte, forventetInntekt)
        ).let { fradrag ->
            fradrag shouldContainAll listOf(expectedEpsInntekt, kontantstøtte, forventetInntekt)
        }
    }

    @Test
    fun `inneholder bare ett fradrag for eps, uavhengig av hvor mange som er input`() {
        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 10000.0)
        val epsForventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 150000.0, tilhører = FradragTilhører.EPS)
        val epsUføretrygd = lagFradrag(Fradragstype.NAVytelserTilLivsopphold, 150000.0, tilhører = FradragTilhører.EPS)
        val epsArbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 5000.0, tilhører = FradragTilhører.EPS)
        val epsKapitalinntekt = lagFradrag(Fradragstype.Kapitalinntekt, 60000.0, tilhører = FradragTilhører.EPS)
        val epsPensjon = lagFradrag(Fradragstype.PrivatPensjon, 15000.0, tilhører = FradragTilhører.EPS)

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(
                forventetInntekt,
                epsForventetInntekt,
                epsUføretrygd,
                epsArbeidsinntekt,
                epsKapitalinntekt,
                epsPensjon
            )
        ).let { fradrag ->
            fradrag.shouldHaveSize(2)
            fradrag.filter { it.getTilhører() == FradragTilhører.BRUKER } shouldHaveSize 1
            fradrag.filter { it.getTilhører() == FradragTilhører.EPS }.let { epsFradrag ->
                epsFradrag shouldHaveSize 1
                epsFradrag.first().let {
                    it.getFradragstype() shouldBe Fradragstype.BeregnetFradragEPS
                    it.getTotaltFradrag() shouldBe epsForventetInntekt.getTotaltFradrag() + epsUføretrygd.getTotaltFradrag() + epsArbeidsinntekt.getTotaltFradrag() + epsKapitalinntekt.getTotaltFradrag() + epsPensjon.getTotaltFradrag()
                    it.getPeriode() shouldBe Periode(1.januar(2020), 31.desember(2020))
                }
            }
        }
    }
}
