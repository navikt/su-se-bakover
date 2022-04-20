package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører.BRUKER
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører.EPS
import no.nav.su.se.bakover.test.månedsperiodeJanuar2020
import org.junit.jupiter.api.Test

internal class EpsUnder67Test {
    @Test
    fun `velger arbeidsinntekt dersom den er større enn forventet inntekt`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val arbeidsinntekt = lagFradrag(Fradragstype(F.Arbeidsinntekt), 2000.0, periode)
        val kontantstøtte = lagFradrag(Fradragstype(F.Kontantstøtte), 500.0, periode)
        val forventetInntekt = lagFradrag(Fradragstype(F.ForventetInntekt), 500.0, periode)

        val expectedArbeidsinntekt =
            lagPeriodisertFradrag(Fradragstype(F.Arbeidsinntekt), 2000.0, månedsperiodeJanuar2020)
        val expectedKontantstøtte =
            lagPeriodisertFradrag(Fradragstype(F.Kontantstøtte), 500.0, månedsperiodeJanuar2020)

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[månedsperiodeJanuar2020]!! shouldContainAll listOf(
                expectedArbeidsinntekt,
                expectedKontantstøtte,
            )
            it.values.forEach { it.none { it.fradragstype.type == F.ForventetInntekt } }
        }
    }

    @Test
    fun `velger forventet inntekt dersom den er større enn arbeidsinntekt`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val arbeidsinntekt = lagFradrag(Fradragstype(F.Arbeidsinntekt), 500.0, periode)
        val kontantstøtte = lagFradrag(Fradragstype(F.Kontantstøtte), 500.0, periode)
        val forventetInntekt = lagFradrag(Fradragstype(F.ForventetInntekt), 2000.0, periode)

        val expectedForventetInntekt =
            lagPeriodisertFradrag(Fradragstype(F.ForventetInntekt), 2000.0, månedsperiodeJanuar2020)
        val expectedKontantstøtte =
            lagPeriodisertFradrag(Fradragstype(F.Kontantstøtte), 500.0, månedsperiodeJanuar2020)

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[månedsperiodeJanuar2020]!! shouldContainAll listOf(
                expectedForventetInntekt,
                expectedKontantstøtte,
            )
            it.values.forEach { it.none { it.fradragstype.type == F.Arbeidsinntekt } }
        }
    }

    @Test
    fun `tar med fradrag som tilhører EPS`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val epsArbeidsinntekt = lagFradrag(Fradragstype(F.Arbeidsinntekt), 2000.0, periode, tilhører = EPS)
        val forventetInntekt = lagFradrag(Fradragstype(F.ForventetInntekt), 1000.0, periode)

        val expectedBrukerInntekt =
            lagPeriodisertFradrag(Fradragstype(F.ForventetInntekt), 1000.0, månedsperiodeJanuar2020)
        val expectedEpsInntekt = lagPeriodisertFradrag(
            Fradragstype(F.BeregnetFradragEPS), 2000.0, månedsperiodeJanuar2020, EPS,
        )

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(epsArbeidsinntekt, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[månedsperiodeJanuar2020]!! shouldBe listOf(
                expectedBrukerInntekt,
                expectedEpsInntekt
            )
            it.values.forEach { it.any { it.tilhører == BRUKER } shouldBe true }
            it.values.forEach { it.any { it.tilhører == EPS } shouldBe true }
        }
    }

    @Test
    fun `inneholder bare ett fradrag for eps, uavhengig av hvor mange som er input`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val forventetInntekt = lagFradrag(Fradragstype(F.ForventetInntekt), 10000.0, periode)
        val epsForventetInntekt = lagFradrag(Fradragstype(F.ForventetInntekt), 150000.0, periode, tilhører = EPS)
        val epsUføretrygd = lagFradrag(Fradragstype(F.NAVytelserTilLivsopphold), 150000.0, periode, tilhører = EPS)
        val epsArbeidsinntekt = lagFradrag(Fradragstype(F.Arbeidsinntekt), 5000.0, periode, tilhører = EPS)
        val epsKapitalinntekt = lagFradrag(Fradragstype(F.Kapitalinntekt), 60000.0, periode, tilhører = EPS)
        val epsPensjon = lagFradrag(Fradragstype(F.PrivatPensjon), 15000.0, periode, tilhører = EPS)

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(
                forventetInntekt,
                epsForventetInntekt,
                epsUføretrygd,
                epsArbeidsinntekt,
                epsKapitalinntekt,
                epsPensjon,
            ),
            beregningsperiode = periode,
        ).let { fradrag ->
            fradrag.size shouldBe 12
            fradrag.values.forEach { alleFradrag ->
                alleFradrag.filter { it.tilhører == EPS }.let { epsFradrag ->
                    epsFradrag shouldHaveSize 1
                    epsFradrag.all { it.fradragstype == Fradragstype(F.BeregnetFradragEPS) } shouldBe true
                }
            }
        }
    }

    @Test
    fun `sosialstønad for EPS går til fradrag uavhengig av om det eksisterer et fribeløp`() {
        val periode = Periode.create(1.mai(2021), 31.desember(2021))

        FradragStrategy.EpsUnder67År.beregn(
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
