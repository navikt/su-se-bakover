package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test

internal class EpsOver67StrategyTest {
    @Test
    fun `EPS sin inntekt skal ikke regnes dersom det er under ordinært minstepensjonsnivå`() {
        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 10.0, tilhører = FradragTilhører.BRUKER)
        val epsArbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 10.0, tilhører = FradragTilhører.EPS)
        val epsKontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 10.0, tilhører = FradragTilhører.EPS)
        val epsForventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 10.0, tilhører = FradragTilhører.EPS)

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntekt, epsKontantstøtte, epsForventetInntekt)
        ).let {
            it shouldBe listOf(forventetInntekt)
        }
    }

    /**
     * Mpn 1.jan = 181908 -> pr.mnd = 15159
     * Mpn 1.mai = 183587 -> pr.mnd = 15298.9166666
     * * Periodisert grense 2020 = (4 * 15159) + (8 * 15298.9166666) = 183027.3333
     */
    @Test
    fun `inkluderer ikke fradrag når sum er lavere enn periodisert minstepensjonsnivå for aktuell 12-månedersperiode`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beløpUnderPeriodisertMinstepensjonsnivå = 183000.0
        val forventetInntekt = lagFradrag(
            type = Fradragstype.ForventetInntekt,
            beløp = 10000.0,
            tilhører = FradragTilhører.BRUKER,
            periode = periode
        )

        val epsArbeidsinntekt = lagFradrag(
            type = Fradragstype.Arbeidsinntekt,
            beløp = beløpUnderPeriodisertMinstepensjonsnivå,
            tilhører = FradragTilhører.EPS,
            periode = periode
        )

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntekt)
        ).let {
            it shouldBe listOf(forventetInntekt)
        }
    }

    /**
     * Mpn 1.jan = 181908 -> pr.mnd = 15159
     * Mpn 1.mai = 183587 -> pr.mnd = 15298.9166666
     * * Periodisert grense 2020 = (4 * 15159) + (8 * 15298.9166666) = 183027.3333
     */
    @Test
    fun `inkluderer fradrag når sum er høyere enn periodisert minstepensjonsnivå for aktuell 12-månedersperiode`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beløpOverPeriodisertMinstepensjonsnivå = 183050.3333
        val forventetInntekt = lagFradrag(
            type = Fradragstype.ForventetInntekt,
            beløp = 10000.0,
            tilhører = FradragTilhører.BRUKER,
            periode = periode
        )

        val epsArbeidsinntekt = lagFradrag(
            type = Fradragstype.Arbeidsinntekt,
            beløp = beløpOverPeriodisertMinstepensjonsnivå,
            tilhører = FradragTilhører.EPS,
            periode = periode
        )

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntekt)
        ).let {
            val (bruker, eps) = it.partition { it.getTilhører() == FradragTilhører.BRUKER }
            bruker shouldContainExactly listOf(forventetInntekt)
            eps shouldHaveSize 1
            eps.first { it.getFradragstype() == Fradragstype.BeregnetFradragEPS }.let {
                it.getTotaltFradrag() shouldBe 23.0.plusOrMinus(0.0001)
                it.getPeriode() shouldBe epsArbeidsinntekt.getPeriode()
            }
        }
    }

    @Test
    fun `inneholder bare et fradrag for eps, uavhengig av hvor mange som er input`() {
        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 10000.0)
        val epsForventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 150000.0, tilhører = FradragTilhører.EPS)
        val epsUføretrygd = lagFradrag(Fradragstype.NAVytelserTilLivsopphold, 150000.0, tilhører = FradragTilhører.EPS)
        val epsArbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 5000.0, tilhører = FradragTilhører.EPS)
        val epsKapitalinntekt = lagFradrag(Fradragstype.Kapitalinntekt, 60000.0, tilhører = FradragTilhører.EPS)
        val epsPensjon = lagFradrag(Fradragstype.PrivatPensjon, 15000.0, tilhører = FradragTilhører.EPS)

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(
                forventetInntekt,
                epsForventetInntekt,
                epsUføretrygd,
                epsArbeidsinntekt,
                epsKapitalinntekt,
                epsPensjon
            )
        ).let {
            it shouldHaveSize 2
            it.count { it.getTilhører() == FradragTilhører.BRUKER } shouldBe 1
            it.count { it.getTilhører() == FradragTilhører.EPS } shouldBe 1
        }
    }
}
