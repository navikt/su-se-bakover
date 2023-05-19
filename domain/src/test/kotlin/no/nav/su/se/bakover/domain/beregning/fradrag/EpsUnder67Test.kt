package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører.BRUKER
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører.EPS
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.Arbeidsinntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.BeregnetFradragEPS
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.ForventetInntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.Kapitalinntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.Kontantstøtte
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.NAVytelserTilLivsopphold
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.PrivatPensjon
import org.junit.jupiter.api.Test

internal class EpsUnder67Test {
    @Test
    fun `velger arbeidsinntekt dersom den er større enn forventet inntekt`() {
        val periode = år(2020)
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 2000.0, periode)
        val kontantstøtte = lagFradrag(Kontantstøtte, 500.0, periode)
        val forventetInntekt = lagFradrag(ForventetInntekt, 500.0, periode)

        val expectedArbeidsinntekt =
            lagPeriodisertFradrag(Arbeidsinntekt, 2000.0, januar(2020))
        val expectedKontantstøtte =
            lagPeriodisertFradrag(Kontantstøtte, 500.0, januar(2020))

        FradragStrategy.Uføre.EpsUnder67År.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[januar(2020)]!! shouldContainAll listOf(
                expectedArbeidsinntekt,
                expectedKontantstøtte,
            )
            it.values.forEach { it.none { it.fradragstype == ForventetInntekt } }
        }
    }

    @Test
    fun `velger forventet inntekt dersom den er større enn arbeidsinntekt`() {
        val periode = år(2020)
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 500.0, periode)
        val kontantstøtte = lagFradrag(Kontantstøtte, 500.0, periode)
        val forventetInntekt = lagFradrag(ForventetInntekt, 2000.0, periode)

        val expectedForventetInntekt =
            lagPeriodisertFradrag(ForventetInntekt, 2000.0, januar(2020))
        val expectedKontantstøtte =
            lagPeriodisertFradrag(Kontantstøtte, 500.0, januar(2020))

        FradragStrategy.Uføre.EpsUnder67År.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[januar(2020)]!! shouldContainAll listOf(
                expectedForventetInntekt,
                expectedKontantstøtte,
            )
            it.values.forEach { it.none { it.fradragstype == Arbeidsinntekt } }
        }
    }

    @Test
    fun `tar med fradrag som tilhører EPS`() {
        val periode = år(2020)
        val epsArbeidsinntekt = lagFradrag(Arbeidsinntekt, 2000.0, periode, tilhører = EPS)
        val forventetInntekt = lagFradrag(ForventetInntekt, 1000.0, periode)

        val expectedBrukerInntekt =
            lagPeriodisertFradrag(ForventetInntekt, 1000.0, januar(2020))
        val expectedEpsInntekt = lagPeriodisertFradrag(
            BeregnetFradragEPS,
            2000.0,
            januar(2020),
            EPS,
        )

        FradragStrategy.Uføre.EpsUnder67År.beregn(
            fradrag = listOf(epsArbeidsinntekt, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[januar(2020)]!! shouldBe listOf(
                expectedBrukerInntekt,
                expectedEpsInntekt,
            )
            it.values.forEach { it.any { it.tilhører == BRUKER } shouldBe true }
            it.values.forEach { it.any { it.tilhører == EPS } shouldBe true }
        }
    }

    @Test
    fun `inneholder bare ett fradrag for eps, uavhengig av hvor mange som er input`() {
        val periode = år(2020)
        val forventetInntekt = lagFradrag(ForventetInntekt, 10000.0, periode)
        val epsForventetInntekt = lagFradrag(ForventetInntekt, 150000.0, periode, tilhører = EPS)
        val epsUføretrygd = lagFradrag(NAVytelserTilLivsopphold, 150000.0, periode, tilhører = EPS)
        val epsArbeidsinntekt = lagFradrag(Arbeidsinntekt, 5000.0, periode, tilhører = EPS)
        val epsKapitalinntekt = lagFradrag(Kapitalinntekt, 60000.0, periode, tilhører = EPS)
        val epsPensjon = lagFradrag(PrivatPensjon, 15000.0, periode, tilhører = EPS)

        FradragStrategy.Uføre.EpsUnder67År.beregn(
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
                    epsFradrag.all { it.fradragstype == BeregnetFradragEPS } shouldBe true
                }
            }
        }
    }

    @Test
    fun `sosialstønad for EPS går til fradrag uavhengig av om det eksisterer et fribeløp`() {
        val periode = Periode.create(1.mai(2021), 31.desember(2021))

        FradragStrategy.Uføre.EpsUnder67År.beregn(
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
