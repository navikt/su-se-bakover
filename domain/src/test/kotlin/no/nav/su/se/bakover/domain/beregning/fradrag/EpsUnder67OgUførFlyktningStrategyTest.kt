package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class EpsUnder67OgUførFlyktningStrategyTest {
    @Test
    fun `EPS sin inntekt skal ikke regnes dersom det er under ordinær sats for uføretrygd`() {
        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 10.0, tilhører = FradragTilhører.BRUKER)
        val epsArbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 10.0, tilhører = FradragTilhører.EPS)
        val epsKontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 10.0, tilhører = FradragTilhører.EPS)
        val epsForventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 10.0, tilhører = FradragTilhører.EPS)

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntekt, epsKontantstøtte, epsForventetInntekt)
        ).let {
            it shouldBe listOf(forventetInntekt)
        }
    }

    /**
     * Mpn 1.jan = 227676.28 -> pr.mnd = 18973.02
     * Mpn 1.mai = 231080.28 -> pr.mnd = 19256.69
     * Periodisert grense 2020 = (4 * 18973.02) + (8 * 19256.69) = 229945.6
     */
    @Test
    fun `inkluderer ikke fradrag når sum er lavere enn periodisert ordinær sats for aktuell 12-månedersperiode`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beløpUnderPeriodisertOrdinærSats = 229944.0
        val forventetInntekt = lagFradrag(
            type = Fradragstype.ForventetInntekt,
            beløp = 10000.0,
            tilhører = FradragTilhører.BRUKER,
            periode = periode
        )

        val epsForventetInntekt = FradragFactory.ny(
            type = Fradragstype.ForventetInntekt,
            beløp = 0.0,
            periode = periode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS
        )

        val epsArbeidsinntekt = lagFradrag(
            type = Fradragstype.Arbeidsinntekt,
            beløp = beløpUnderPeriodisertOrdinærSats,
            tilhører = FradragTilhører.EPS,
            periode = periode
        )

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(forventetInntekt, epsForventetInntekt, epsArbeidsinntekt)
        ).let {
            it shouldBe listOf(forventetInntekt)
        }
    }

    /**
     * Mpn 1.jan = 227676.28 -> pr.mnd = 18973.02
     * Mpn 1.mai = 231080.28 -> pr.mnd = 19256.69
     * Periodisert grense 2020 = (4 * 18973.02) + (8 * 19256.69) = 229945.6
     */
    @Test
    fun `inkluderer fradrag når sum er høyere enn periodisert ordinær sats for aktuell 12-månedersperiode`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beløpOverPeriodisertOrdinærSats = 229947.6
        val forventetInntekt = lagFradrag(
            type = Fradragstype.ForventetInntekt,
            beløp = 10000.0,
            tilhører = FradragTilhører.BRUKER,
            periode = periode
        )

        val epsForventetInntekt = FradragFactory.ny(
            type = Fradragstype.ForventetInntekt,
            beløp = 0.0,
            periode = periode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS
        )

        val epsArbeidsinntekt = lagFradrag(
            type = Fradragstype.Arbeidsinntekt,
            beløp = beløpOverPeriodisertOrdinærSats,
            tilhører = FradragTilhører.EPS,
            periode = periode
        )

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(forventetInntekt, epsForventetInntekt, epsArbeidsinntekt)
        ).let {
            val (bruker, eps) = it.partition { it.getTilhører() == FradragTilhører.BRUKER }
            bruker shouldContainExactly listOf(forventetInntekt)
            eps shouldHaveSize 1
            eps.first { it.getFradragstype() == Fradragstype.Arbeidsinntekt }.let {
                it.getTotaltFradrag() shouldBe 2.0.plusOrMinus(0.0001)
                it.getPeriode() shouldBe epsArbeidsinntekt.getPeriode()
            }
        }
    }

    @Test
    fun `godtar ikke fradrag hvor antall måneder ikke er 12 - denne oppførselene er udefinert`() {
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
                listOf(
                    lagFradrag(
                        type = Fradragstype.ForventetInntekt,
                        beløp = 10000.0,
                        tilhører = FradragTilhører.BRUKER,
                        periode = Periode(1.januar(2020), 31.januar(2020))
                    )
                )
            )
        }
    }

    @Test
    fun `eps må oppgi nøyaktig en forventet inntekt`() {
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
                listOf(
                    lagFradrag(
                        type = Fradragstype.ForventetInntekt,
                        beløp = 10000.0,
                        tilhører = FradragTilhører.BRUKER,
                        periode = Periode(1.januar(2020), 31.desember(2020))
                    )
                )
            )
        }.let {
            it.message shouldContain "Fradrag må inneholde EPSs forventede inntekt etter uførhet."
        }

        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
                listOf(
                    lagFradrag(
                        type = Fradragstype.ForventetInntekt,
                        beløp = 10000.0,
                        tilhører = FradragTilhører.BRUKER,
                        periode = Periode(1.januar(2020), 31.desember(2020))
                    ),
                    lagFradrag(
                        type = Fradragstype.ForventetInntekt,
                        beløp = 10000.0,
                        tilhører = FradragTilhører.EPS,
                        periode = Periode(1.januar(2020), 31.desember(2020))
                    ),
                    lagFradrag(
                        type = Fradragstype.ForventetInntekt,
                        beløp = 10000.0,
                        tilhører = FradragTilhører.EPS,
                        periode = Periode(1.januar(2020), 31.desember(2020))
                    )
                )
            )
        }.let {
            it.message shouldContain "Fradrag må inneholde EPSs forventede inntekt etter uførhet."
        }
    }
}
