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
 * Garantipensjon 1.jan = 176099 -> pr.mnd = 14674.9166666
 * Garantipensjon 1.mai = 177724 -> pr.mnd = 14810.3333333
 * * Periodisert grense 2020 = (4 * 14674.9166666) + (8 * 14810.3333333) = 177182.333333
 */
internal class EpsOver67StrategyTest {

    @Test
    fun `inkluderer ikke fradrag for EPS som er lavere enn ordinært garantipensjonsnivå for aktuell måned`() {
        val periode = Periode.create(1.januar(2020), 31.januar(2020))
        val forventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 12000.0, periode, tilhører = BRUKER)
        val epsArbeidsinntekt = lagPeriodisertFradrag(Arbeidsinntekt, 5000.0, periode, tilhører = EPS)

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntekt),
            beregningsperiode = periode
        ).let {
            it shouldHaveSize 1
            it.values.forEach { it shouldBe listOf(forventetInntekt) }
        }
    }

    @Test
    fun `inkluderer fradrag for EPS som overstiger ordinært garantipensjonsnivå for aktuell måned`() {
        val periode = Periode.create(1.januar(2020), 31.januar(2020))
        val forventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 12000.0, periode, tilhører = BRUKER)
        val epsArbeidsinntekt = lagPeriodisertFradrag(Arbeidsinntekt, 20000.0, periode, tilhører = EPS)

        val expectedEpsFradrag = lagPeriodisertFradrag(BeregnetFradragEPS, 20000.0 - 14674.916666666666667, periode, tilhører = EPS)

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntekt),
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
            lagFradrag(Arbeidsinntekt, 20000.0, Periode.create(1.januar(2020), 31.januar(2020)), tilhører = EPS)
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
                20000.0 - 14674.916666666666667,
                Periode.create(1.januar(2020), 31.januar(2020)),
                tilhører = EPS
            )
        val expectedEpsFradragJuli =
            lagPeriodisertFradrag(
                BeregnetFradragEPS,
                20000.0 - 14810.333333333333333,
                Periode.create(1.juli(2020), 31.juli(2020)),
                tilhører = EPS
            )

        FradragStrategy.EpsOver67År.beregn(
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
    fun `inneholder bare en fradragstype for EPS uavhengig av hvor mange som sendes sinn`() {
        val periode = Periode.create(1.januar(2020), 30.april(2020))
        val forventetInntekt = lagFradrag(ForventetInntekt, 10000.0, periode)
        val epsArbeidsinntekt = lagFradrag(Arbeidsinntekt, 5000.0, periode, tilhører = EPS)
        val epsKapitalinntekt = lagFradrag(Kapitalinntekt, 60000.0, periode, tilhører = EPS)
        val epsPensjon = lagFradrag(PrivatPensjon, 15000.0, periode, tilhører = EPS)

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(
                forventetInntekt,
                epsArbeidsinntekt,
                epsKapitalinntekt,
                epsPensjon
            ),
            beregningsperiode = periode
        ).let {
            it shouldHaveSize 4
            it.values.forEach {
                it.filter { it.tilhører == EPS }.all { it.fradragstype == BeregnetFradragEPS }
            }
        }
    }

    @Test
    fun `fungerer uavhengig av om EPS har fradrag`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val forventetInntekt = lagFradrag(ForventetInntekt, 1000.0, periode)
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 2000.0, periode)

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, arbeidsinntekt),
            beregningsperiode = periode
        ).let {
            it shouldHaveSize 12
            it.values.forEach { it.sumOf { it.månedsbeløp } shouldBe arbeidsinntekt.månedsbeløp }
            it.values.forEach { it.none { it.tilhører == EPS } shouldBe true }
        }
    }
}
