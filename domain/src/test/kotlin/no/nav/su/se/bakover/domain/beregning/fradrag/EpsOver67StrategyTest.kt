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
 * Mpn 1.jan = 181908 -> pr.mnd = 15159
 * Mpn 1.mai = 183587 -> pr.mnd = 15298.9166666
 * * Periodisert grense 2020 = (4 * 15159) + (8 * 15298.9166666) = 183027.3333
 */
internal class EpsOver67StrategyTest {

    @Test
    fun `inkluderer ikke fradrag for EPS som er lavere enn ordinært minstepensjonsnivå for aktuell måned`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
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
    fun `inkluderer fradrag for EPS som overstiger ordinært minstepensjonsnivå for aktuell måned`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
        val forventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 12000.0, periode, tilhører = BRUKER)
        val epsArbeidsinntekt = lagPeriodisertFradrag(Arbeidsinntekt, 20000.0, periode, tilhører = EPS)

        val expectedEpsFradrag = lagPeriodisertFradrag(BeregnetFradragEPS, 20000.0 - 15159, periode, tilhører = EPS)

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
            lagFradrag(ForventetInntekt, 12000.0, Periode(1.januar(2020), 31.desember(2020)), tilhører = BRUKER)
        val epsArbeidsinntektJan =
            lagFradrag(Arbeidsinntekt, 20000.0, Periode(1.januar(2020), 31.januar(2020)), tilhører = EPS)
        val epsArbeidsinntektJuli =
            lagFradrag(Arbeidsinntekt, 20000.0, Periode(1.juli(2020), 31.juli(2020)), tilhører = EPS)

        val expectedFradragBrukerJan =
            lagPeriodisertFradrag(ForventetInntekt, 1000.0, Periode(1.januar(2020), 31.januar(2020)), tilhører = BRUKER)
        val expectedFradragBrukerMars =
            lagPeriodisertFradrag(ForventetInntekt, 1000.0, Periode(1.mars(2020), 31.mars(2020)), tilhører = BRUKER)
        val expectedFradragBrukerJuli =
            lagPeriodisertFradrag(ForventetInntekt, 1000.0, Periode(1.juli(2020), 31.juli(2020)), tilhører = BRUKER)
        val expectedEpsFradragJan =
            lagPeriodisertFradrag(
                BeregnetFradragEPS,
                20000.0 - 15159,
                Periode(1.januar(2020), 31.januar(2020)),
                tilhører = EPS
            )
        val expectedEpsFradragJuli =
            lagPeriodisertFradrag(
                BeregnetFradragEPS,
                20000.0 - 15298.916666666666,
                Periode(1.juli(2020), 31.juli(2020)),
                tilhører = EPS
            )

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntektJan, epsArbeidsinntektJuli),
            beregningsperiode = Periode(1.januar(2020), 31.desember(2020))
        ).let {
            it shouldHaveSize 12
            it[Periode(1.januar(2020), 31.januar(2020))]!! shouldContainAll listOf(
                expectedFradragBrukerJan,
                expectedEpsFradragJan
            )
            it[Periode(1.mars(2020), 31.mars(2020))]!! shouldBe listOf(expectedFradragBrukerMars)
            it[Periode(1.juli(2020), 31.juli(2020))]!! shouldContainAll listOf(
                expectedFradragBrukerJuli,
                expectedEpsFradragJuli
            )
        }
    }

    @Test
    fun `inneholder bare en fradragstype for EPS uavhengig av hvor mange som sendes sinn`() {
        val periode = Periode(1.januar(2020), 30.april(2020))
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
                it.filter { it.getTilhører() == EPS }.all { it.getFradragstype() == BeregnetFradragEPS }
            }
        }
    }

    @Test
    fun `fungerer uavhengig av om EPS har fradrag`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val forventetInntekt = lagFradrag(ForventetInntekt, 12000.0, periode)
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 24000.0, periode)

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, arbeidsinntekt),
            beregningsperiode = periode
        ).let {
            it shouldHaveSize 12
            it.values.forEach {
                it.sumByDouble { it.getTotaltFradrag() } shouldBe
                    arbeidsinntekt.getTotaltFradrag() / arbeidsinntekt.getPeriode().getAntallMåneder()
            }
            it.values.forEach { it.none { it.getTilhører() == EPS } shouldBe true }
        }
    }
}
