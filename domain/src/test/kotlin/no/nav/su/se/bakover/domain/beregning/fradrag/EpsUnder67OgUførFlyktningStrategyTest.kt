package no.nav.su.se.bakover.domain.beregning.fradrag

import beregning.domain.fradrag.FradragTilhører.BRUKER
import beregning.domain.fradrag.FradragTilhører.EPS
import beregning.domain.fradrag.Fradragstype
import beregning.domain.fradrag.Fradragstype.Arbeidsinntekt
import beregning.domain.fradrag.Fradragstype.BeregnetFradragEPS
import beregning.domain.fradrag.Fradragstype.ForventetInntekt
import beregning.domain.fradrag.Fradragstype.Kapitalinntekt
import beregning.domain.fradrag.Fradragstype.PrivatPensjon
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test

/**
 * Sats 1.jan = 227676.28 -> pr.mnd = 18973.02
 * Sats 1.mai = 231080.28 -> pr.mnd = 19256.69
 * Periodisert grense 2020 = (4 * 18973.02) + (8 * 19256.69) = 229945.6
 */
internal class EpsUnder67OgUførFlyktningStrategyTest {

    @Test
    fun `inkluderer ikke fradrag for EPS som er lavere enn ordinær sats for uføretrygd for aktuell måned`() {
        val periode = januar(2020)
        val forventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 12000.0, periode, tilhører = BRUKER)
        val epsForventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 5000.0, periode, tilhører = EPS)

        FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato()).beregn(
            fradrag = listOf(forventetInntekt, epsForventetInntekt),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 1
            it.values.forEach { it shouldBe listOf(forventetInntekt) }
        }
    }

    @Test
    fun `inkluderer fradrag for EPS som overstiger ordinær sats for uføretrygd for aktuell måned`() {
        val periode = januar(2020)
        val forventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 12000.0, periode, tilhører = BRUKER)
        val epsForventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 20000.0, periode, tilhører = EPS)

        val expectedEpsFradrag = lagPeriodisertFradrag(BeregnetFradragEPS, 20000.0 - 18973.02, periode, tilhører = EPS)

        FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato()).beregn(
            fradrag = listOf(forventetInntekt, epsForventetInntekt),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 1
            it.values.forEach { it shouldContainAll listOf(forventetInntekt, expectedEpsFradrag) }
        }
    }

    @Test
    fun `varierer mellom å inkludere og ikke inkludere EPS sine fradrag`() {
        val forventetInntekt =
            lagFradrag(ForventetInntekt, 1000.0, år(2020), tilhører = BRUKER)
        val epsArbeidsinntektJan =
            lagFradrag(ForventetInntekt, 20000.0, januar(2020), tilhører = EPS)
        val epsArbeidsinntektJuli =
            lagFradrag(Arbeidsinntekt, 20000.0, juli(2020), tilhører = EPS)

        val expectedFradragBrukerJan =
            lagPeriodisertFradrag(
                ForventetInntekt,
                1000.0,
                januar(2020),
                tilhører = BRUKER,
            )
        val expectedFradragBrukerMars =
            lagPeriodisertFradrag(
                ForventetInntekt,
                1000.0,
                mars(2020),
                tilhører = BRUKER,
            )
        val expectedFradragBrukerJuli =
            lagPeriodisertFradrag(
                ForventetInntekt,
                1000.0,
                juli(2020),
                tilhører = BRUKER,
            )
        val expectedEpsFradragJan =
            lagPeriodisertFradrag(
                BeregnetFradragEPS,
                20000.0 - 18973.02,
                januar(2020),
                tilhører = EPS,
            )
        val expectedEpsFradragJuli =
            lagPeriodisertFradrag(
                BeregnetFradragEPS,
                20000.0 - 19256.69,
                juli(2020),
                tilhører = EPS,
            )

        FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato()).beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntektJan, epsArbeidsinntektJuli),
            beregningsperiode = år(2020),
        ).let {
            it shouldHaveSize 12
            it[januar(2020)]!! shouldContainAll listOf(
                expectedFradragBrukerJan,
                expectedEpsFradragJan,
            )
            it[mars(2020)]!! shouldBe listOf(expectedFradragBrukerMars)
            it[juli(2020)]!! shouldContainAll listOf(
                expectedFradragBrukerJuli,
                expectedEpsFradragJuli,
            )
        }
    }

    @Test
    fun `inneholder bare en fradragstype for EPS uavhengig av hvor mange som sendes inn`() {
        val periode = Periode.create(1.januar(2020), 30.april(2020))
        val forventetInntekt = lagFradrag(ForventetInntekt, 10000.0, periode)
        val epsForventetInntekt = lagFradrag(ForventetInntekt, 5000.0, periode, tilhører = EPS)
        val epsKapitalinntekt = lagFradrag(Kapitalinntekt, 60000.0, periode, tilhører = EPS)
        val epsPensjon = lagFradrag(PrivatPensjon, 15000.0, periode, tilhører = EPS)

        FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato()).beregn(
            fradrag = listOf(
                forventetInntekt,
                epsForventetInntekt,
                epsKapitalinntekt,
                epsPensjon,
            ),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 4
            it.values.forEach {
                it.filter { it.tilhører == EPS }
                    .all { it.fradragstype == BeregnetFradragEPS }
            }
        }
    }

    @Test
    fun `kan beregne fradrag for EPS uten forventet inntekt og arbeidsinntekt`() {
        val periode = januar(2020)
        val forventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 10000.0, periode)
        val epsKapitalinntekt = lagPeriodisertFradrag(Kapitalinntekt, 10000.0, periode, tilhører = EPS)
        val epsPrivatPensjon = lagPeriodisertFradrag(PrivatPensjon, 10000.0, periode, tilhører = EPS)

        val expectedBeregnetEpsFradrag = lagPeriodisertFradrag(
            type = BeregnetFradragEPS,
            beløp = (epsKapitalinntekt.månedsbeløp + epsPrivatPensjon.månedsbeløp - 18973.02),
            periode,
            tilhører = EPS,
        )

        FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato()).beregn(
            fradrag = listOf(forventetInntekt, epsKapitalinntekt, epsPrivatPensjon),
            beregningsperiode = periode,
        ).let {
            it[januar(2020)]!! shouldContainAll listOf(
                forventetInntekt,
                expectedBeregnetEpsFradrag,
            )
        }
    }

    @Test
    fun `fungerer uavhengig av om EPS har fradrag`() {
        val periode = år(2020)
        val forventetInntekt = lagFradrag(ForventetInntekt, 1000.0, periode)
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 2000.0, periode)

        FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato()).beregn(
            fradrag = listOf(forventetInntekt, arbeidsinntekt),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 12
            it.values.forEach { it.sumOf { it.månedsbeløp } shouldBe arbeidsinntekt.månedsbeløp }
            it.values.forEach { it.none { it.tilhører == EPS } shouldBe true }
        }
    }

    @Test
    fun `sosialstønad for EPS går til fradrag uavhengig av om det eksisterer et fribeløp`() {
        val periode = Periode.create(1.mai(2021), 31.desember(2021))

        FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato()).beregn(
            fradrag = listOf(
                lagFradrag(ForventetInntekt, 0.0, periode),
                lagFradrag(Fradragstype.Sosialstønad, 5000.0, periode, EPS),
            ),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 8
            it.values.sumOf { it.sumOf { it.månedsbeløp } }
        } shouldBe 8 * 5000
    }
}
