package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører.BRUKER
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører.EPS
import no.nav.su.se.bakover.test.månedsperiodeJanuar2020
import no.nav.su.se.bakover.test.månedsperiodeJuli2020
import no.nav.su.se.bakover.test.månedsperiodeMars2020
import org.junit.jupiter.api.Test

/**
 * Garantipensjon 1.jan = 176099 -> pr.mnd = 14674.9166666
 * Garantipensjon 1.mai = 177724 -> pr.mnd = 14810.3333333
 * * Periodisert grense 2020 = (4 * 14674.9166666) + (8 * 14810.3333333) = 177182.333333
 */
internal class EpsOver67StrategyTest {

    @Test
    fun `inkluderer ikke fradrag for EPS som er lavere enn ordinært garantipensjonsnivå for aktuell måned`() {
        val periode = månedsperiodeJanuar2020
        val forventetInntekt = lagPeriodisertFradrag(Fradragstype(F.ForventetInntekt), 12000.0, periode, tilhører = BRUKER)
        val epsArbeidsinntekt = lagPeriodisertFradrag(Fradragstype(F.Arbeidsinntekt), 5000.0, periode, tilhører = EPS)

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntekt),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 1
            it.values.forEach { it shouldBe listOf(forventetInntekt) }
        }
    }

    @Test
    fun `inkluderer fradrag for EPS som overstiger ordinært garantipensjonsnivå for aktuell måned`() {
        val periode = månedsperiodeJanuar2020
        val forventetInntekt = lagPeriodisertFradrag(Fradragstype(F.ForventetInntekt), 12000.0, periode, tilhører = BRUKER)
        val epsArbeidsinntekt = lagPeriodisertFradrag(Fradragstype(F.Arbeidsinntekt), 20000.0, periode, tilhører = EPS)

        val expectedEpsFradrag =
            lagPeriodisertFradrag(Fradragstype(F.BeregnetFradragEPS), 20000.0 - 14674.916666666666667, periode, tilhører = EPS)

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntekt),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 1
            it.values.forEach { it shouldContainAll listOf(forventetInntekt, expectedEpsFradrag) }
        }
    }

    @Test
    fun `varierer mellom å inkludere og ikke inkludere EPS sine fradrag`() {
        val forventetInntekt =
            lagFradrag(Fradragstype(F.ForventetInntekt), 1000.0, Periode.create(1.januar(2020), 31.desember(2020)), tilhører = BRUKER)
        val epsArbeidsinntektJan =
            lagFradrag(Fradragstype(F.Arbeidsinntekt), 20000.0, månedsperiodeJanuar2020, tilhører = EPS)
        val epsArbeidsinntektJuli =
            lagFradrag(Fradragstype(F.Arbeidsinntekt), 20000.0, månedsperiodeJuli2020, tilhører = EPS)

        val expectedFradragBrukerJan =
            lagPeriodisertFradrag(
                Fradragstype(F.ForventetInntekt),
                1000.0,
                månedsperiodeJanuar2020,
                tilhører = BRUKER,
            )
        val expectedFradragBrukerMars =
            lagPeriodisertFradrag(
                Fradragstype(F.ForventetInntekt),
                1000.0,
                månedsperiodeMars2020,
                tilhører = BRUKER,
            )
        val expectedFradragBrukerJuli =
            lagPeriodisertFradrag(
                Fradragstype(F.ForventetInntekt),
                1000.0,
                månedsperiodeJuli2020,
                tilhører = BRUKER,
            )
        val expectedEpsFradragJan =
            lagPeriodisertFradrag(
                Fradragstype(F.BeregnetFradragEPS),
                20000.0 - 14674.916666666666,
                månedsperiodeJanuar2020,
                tilhører = EPS,
            )
        val expectedEpsFradragJuli =
            lagPeriodisertFradrag(
                Fradragstype(F.BeregnetFradragEPS),
                20000.0 - 14810.333333333334,
                månedsperiodeJuli2020,
                tilhører = EPS,
            )

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntektJan, epsArbeidsinntektJuli),
            beregningsperiode = Periode.create(1.januar(2020), 31.desember(2020)),
        ).let {
            it shouldHaveSize 12
            it[månedsperiodeJanuar2020]!! shouldContainAll listOf(
                expectedFradragBrukerJan,
                expectedEpsFradragJan,
            )
            it[månedsperiodeMars2020]!! shouldBe listOf(expectedFradragBrukerMars)
            it[månedsperiodeJuli2020]!! shouldContainAll listOf(
                expectedFradragBrukerJuli,
                expectedEpsFradragJuli,
            )
        }
    }

    @Test
    fun `inneholder bare en fradragstype for EPS uavhengig av hvor mange som sendes sinn`() {
        val periode = Periode.create(1.januar(2020), 30.april(2020))
        val forventetInntekt = lagFradrag(Fradragstype(F.ForventetInntekt), 10000.0, periode)
        val epsArbeidsinntekt = lagFradrag(Fradragstype(F.Arbeidsinntekt), 5000.0, periode, tilhører = EPS)
        val epsKapitalinntekt = lagFradrag(Fradragstype(F.Kapitalinntekt), 60000.0, periode, tilhører = EPS)
        val epsPensjon = lagFradrag(Fradragstype(F.PrivatPensjon), 15000.0, periode, tilhører = EPS)

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(
                forventetInntekt,
                epsArbeidsinntekt,
                epsKapitalinntekt,
                epsPensjon,
            ),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 4
            it.values.forEach {
                it.filter { it.tilhører == EPS }.all { it.fradragstype.type == F.BeregnetFradragEPS }
            }
        }
    }

    @Test
    fun `fungerer uavhengig av om EPS har fradrag`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val forventetInntekt = lagFradrag(Fradragstype(F.ForventetInntekt), 1000.0, periode)
        val arbeidsinntekt = lagFradrag(Fradragstype(F.Arbeidsinntekt), 2000.0, periode)

        FradragStrategy.EpsOver67År.beregn(
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

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(
                lagFradrag(Fradragstype(F.ForventetInntekt), 0.0, periode),
                lagFradrag(Fradragstype(F.Sosialstønad), 5000.0, periode, EPS),
            ),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 8
            it.values.sumOf { it.sumOf { it.månedsbeløp } }
        } shouldBe 8 * 5000
    }
}
