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
 * Sats 1.jan = 227676.28 -> pr.mnd = 18973.02
 * Sats 1.mai = 231080.28 -> pr.mnd = 19256.69
 * Periodisert grense 2020 = (4 * 18973.02) + (8 * 19256.69) = 229945.6
 */
internal class EpsUnder67OgUførFlyktningStrategyTest {

    @Test
    fun `inkluderer ikke fradrag for EPS som er lavere enn ordinær sats for uføretrygd for aktuell måned`() {
        val periode = månedsperiodeJanuar2020
        val forventetInntekt = lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 12000.0, periode, tilhører = BRUKER)
        val epsForventetInntekt = lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 5000.0, periode, tilhører = EPS)

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(forventetInntekt, epsForventetInntekt),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 1
            it.values.forEach { it shouldBe listOf(forventetInntekt) }
        }
    }

    @Test
    fun `inkluderer fradrag for EPS som overstiger ordinær sats for uføretrygd for aktuell måned`() {
        val periode = månedsperiodeJanuar2020
        val forventetInntekt = lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 12000.0, periode, tilhører = BRUKER)
        val epsForventetInntekt = lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 20000.0, periode, tilhører = EPS)

        val expectedEpsFradrag = lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.BeregnetFradragEPS), 20000.0 - 18973.02, periode, tilhører = EPS)

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
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
            lagFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 1000.0, Periode.create(1.januar(2020), 31.desember(2020)), tilhører = BRUKER)
        val epsArbeidsinntektJan =
            lagFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 20000.0, månedsperiodeJanuar2020, tilhører = EPS)
        val epsArbeidsinntektJuli =
            lagFradrag(FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt), 20000.0, månedsperiodeJuli2020, tilhører = EPS)

        val expectedFradragBrukerJan =
            lagPeriodisertFradrag(
                FradragskategoriWrapper(Fradragskategori.ForventetInntekt),
                1000.0,
                månedsperiodeJanuar2020,
                tilhører = BRUKER,
            )
        val expectedFradragBrukerMars =
            lagPeriodisertFradrag(
                FradragskategoriWrapper(Fradragskategori.ForventetInntekt),
                1000.0,
                månedsperiodeMars2020,
                tilhører = BRUKER,
            )
        val expectedFradragBrukerJuli =
            lagPeriodisertFradrag(
                FradragskategoriWrapper(Fradragskategori.ForventetInntekt),
                1000.0,
                månedsperiodeJuli2020,
                tilhører = BRUKER,
            )
        val expectedEpsFradragJan =
            lagPeriodisertFradrag(
                FradragskategoriWrapper(Fradragskategori.BeregnetFradragEPS),
                20000.0 - 18973.02,
                månedsperiodeJanuar2020,
                tilhører = EPS,
            )
        val expectedEpsFradragJuli =
            lagPeriodisertFradrag(
                FradragskategoriWrapper(Fradragskategori.BeregnetFradragEPS), 20000.0 - 19256.69, månedsperiodeJuli2020, tilhører = EPS,
            )

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
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
    fun `inneholder bare en fradragstype for EPS uavhengig av hvor mange som sendes inn`() {
        val periode = Periode.create(1.januar(2020), 30.april(2020))
        val forventetInntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 10000.0, periode)
        val epsForventetInntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 5000.0, periode, tilhører = EPS)
        val epsKapitalinntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.Kapitalinntekt), 60000.0, periode, tilhører = EPS)
        val epsPensjon = lagFradrag(FradragskategoriWrapper(Fradragskategori.PrivatPensjon), 15000.0, periode, tilhører = EPS)

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
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
                    .all { it.fradragskategoriWrapper.kategori == Fradragskategori.BeregnetFradragEPS }
            }
        }
    }

    @Test
    fun `kan beregne fradrag for EPS uten forventet inntekt og arbeidsinntekt`() {
        val periode = månedsperiodeJanuar2020
        val forventetInntekt = lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 10000.0, periode)
        val epsKapitalinntekt = lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.Kapitalinntekt), 10000.0, periode, tilhører = EPS)
        val epsPrivatPensjon = lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.PrivatPensjon), 10000.0, periode, tilhører = EPS)

        val expectedBeregnetEpsFradrag = lagPeriodisertFradrag(
            type = FradragskategoriWrapper(Fradragskategori.BeregnetFradragEPS),
            beløp = (epsKapitalinntekt.månedsbeløp + epsPrivatPensjon.månedsbeløp - 18973.02),
            periode,
            tilhører = EPS,
        )

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(forventetInntekt, epsKapitalinntekt, epsPrivatPensjon),
            beregningsperiode = periode,
        ).let {
            it[månedsperiodeJanuar2020]!! shouldContainAll listOf(
                forventetInntekt,
                expectedBeregnetEpsFradrag,
            )
        }
    }

    @Test
    fun `fungerer uavhengig av om EPS har fradrag`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val forventetInntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 1000.0, periode)
        val arbeidsinntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt), 2000.0, periode)

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
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

        FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
            fradrag = listOf(
                lagFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 0.0, periode),
                lagFradrag(FradragskategoriWrapper(Fradragskategori.Sosialstønad), 5000.0, periode, EPS),
            ),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 8
            it.values.sumOf { it.sumOf { it.månedsbeløp } }
        } shouldBe 8 * 5000
    }
}
