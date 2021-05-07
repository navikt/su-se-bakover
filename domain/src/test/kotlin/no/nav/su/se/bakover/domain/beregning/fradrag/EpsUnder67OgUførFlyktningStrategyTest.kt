package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører.BRUKER
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører.EPS
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.Arbeidsinntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.BeregnetFradragEPS
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.ForventetInntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.Kapitalinntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.PrivatPensjon
import org.junit.jupiter.api.Test

/**
 * Sats 1.jan = 227676.28 -> pr.mnd = 18973.02
 * Sats 1.mai = 231080.28 -> pr.mnd = 19256.69
 * Periodisert grense 2020 = (4 * 18973.02) + (8 * 19256.69) = 229945.6
 */
internal class EpsUnder67OgUførFlyktningStrategyTest {

    @Test
    fun `inkluderer ikke fradrag for EPS som er lavere enn ordinær sats for uføretrygd for aktuell måned`() {
        val periode = Periode.create(1.januar(2020), 31.januar(2020))
        val forventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 12000.0, periode, tilhører = BRUKER)
        val epsForventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 5000.0, periode, tilhører = EPS)

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(forventetInntekt, epsForventetInntekt),
            beregningsperiode = periode
        ).let {
            it shouldHaveSize 1
            it.values.forEach { it shouldBe listOf(forventetInntekt) }
        }
    }

    @Test
    fun `inkluderer fradrag for EPS som overstiger ordinær sats for uføretrygd for aktuell måned`() {
        val periode = Periode.create(1.januar(2020), 31.januar(2020))
        val forventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 12000.0, periode, tilhører = BRUKER)
        val epsForventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 20000.0, periode, tilhører = EPS)

        val expectedEpsFradrag = lagPeriodisertFradrag(BeregnetFradragEPS, 20000.0 - 18973.02, periode, tilhører = EPS)

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(forventetInntekt, epsForventetInntekt),
            beregningsperiode = periode
        ).let {
            it shouldHaveSize 1
            it.values.forEach { it shouldContainAll listOf(forventetInntekt, expectedEpsFradrag) }
        }
    }

    @Test
    fun `varierer mellom å inkludere og ikke inkludere EPS sine fradrag`() {
        val forventetInntekt =
            lagFradrag(ForventetInntekt, 1000.0, Periode.create(1.januar(2020), 31.desember(2020)), tilhører = BRUKER)
        val epsArbeidsinntektJan =
            lagFradrag(ForventetInntekt, 20000.0, Periode.create(1.januar(2020), 31.januar(2020)), tilhører = EPS)
        val epsArbeidsinntektJuli =
            lagFradrag(Arbeidsinntekt, 20000.0, Periode.create(1.juli(2020), 31.juli(2020)), tilhører = EPS)

        val expectedFradragBrukerJan =
            lagPeriodisertFradrag(ForventetInntekt, 1000.0, Periode.create(1.januar(2020), 31.januar(2020)), tilhører = BRUKER)
        val expectedFradragBrukerMars =
            lagPeriodisertFradrag(ForventetInntekt, 1000.0, Periode.create(1.mars(2020), 31.mars(2020)), tilhører = BRUKER)
        val expectedFradragBrukerJuli =
            lagPeriodisertFradrag(ForventetInntekt, 1000.0, Periode.create(1.juli(2020), 31.juli(2020)), tilhører = BRUKER)
        val expectedEpsFradragJan =
            lagPeriodisertFradrag(
                BeregnetFradragEPS,
                20000.0 - 18973.02,
                Periode.create(1.januar(2020), 31.januar(2020)),
                tilhører = EPS
            )
        val expectedEpsFradragJuli =
            lagPeriodisertFradrag(
                BeregnetFradragEPS, 20000.0 - 19256.69, Periode.create(1.juli(2020), 31.juli(2020)), tilhører = EPS
            )

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntektJan, epsArbeidsinntektJuli),
            beregningsperiode = Periode.create(1.januar(2020), 31.desember(2020))
        ).let {
            it shouldHaveSize 12
            it[Periode.create(1.januar(2020), 31.januar(2020))]!! shouldContainAll listOf(
                expectedFradragBrukerJan,
                expectedEpsFradragJan
            )
            it[Periode.create(1.mars(2020), 31.mars(2020))]!! shouldBe listOf(expectedFradragBrukerMars)
            it[Periode.create(1.juli(2020), 31.juli(2020))]!! shouldContainAll listOf(
                expectedFradragBrukerJuli,
                expectedEpsFradragJuli
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

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(
                forventetInntekt,
                epsForventetInntekt,
                epsKapitalinntekt,
                epsPensjon
            ),
            beregningsperiode = periode
        ).let {
            it shouldHaveSize 4
            it.values.forEach {
                it.filter { it.getTilhører() == EPS }
                    .all { it.getFradragstype() == BeregnetFradragEPS }
            }
        }
    }

    @Test
    fun `kan beregne fradrag for EPS uten forventet inntekt og arbeidsinntekt`() {
        val periode = Periode.create(1.januar(2020), 31.januar(2020))
        val forventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 10000.0, periode)
        val epsKapitalinntekt = lagPeriodisertFradrag(Kapitalinntekt, 10000.0, periode, tilhører = EPS)
        val epsPrivatPensjon = lagPeriodisertFradrag(PrivatPensjon, 10000.0, periode, tilhører = EPS)

        val expectedBeregnetEpsFradrag = lagPeriodisertFradrag(
            type = BeregnetFradragEPS,
            beløp = (epsKapitalinntekt.getMånedsbeløp() + epsPrivatPensjon.getMånedsbeløp() - 18973.02),
            periode,
            tilhører = EPS
        )

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(forventetInntekt, epsKapitalinntekt, epsPrivatPensjon),
            beregningsperiode = periode
        ).let {
            it[Periode.create(1.januar(2020), 31.januar(2020))]!! shouldContainAll listOf(
                forventetInntekt,
                expectedBeregnetEpsFradrag
            )
        }
    }

    @Test
    fun `fungerer uavhengig av om EPS har fradrag`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val forventetInntekt = lagFradrag(ForventetInntekt, 1000.0, periode)
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 2000.0, periode)

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(forventetInntekt, arbeidsinntekt),
            beregningsperiode = periode
        ).let {
            it shouldHaveSize 12
            it.values.forEach { it.sumOf { it.getMånedsbeløp() } shouldBe arbeidsinntekt.getMånedsbeløp() }
            it.values.forEach { it.none { it.getTilhører() == EPS } shouldBe true }
        }
    }
}
