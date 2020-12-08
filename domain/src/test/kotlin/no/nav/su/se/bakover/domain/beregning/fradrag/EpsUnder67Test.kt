package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
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
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 2000.0, periode)
        val kontantstøtte = lagFradrag(Kontantstøtte, 500.0, periode)
        val forventetInntekt = lagFradrag(ForventetInntekt, 500.0, periode)

        val expectedArbeidsinntekt =
            lagPeriodisertFradrag(Arbeidsinntekt, 2000.0, Periode(1.januar(2020), 31.januar(2020)))
        val expectedKontantstøtte =
            lagPeriodisertFradrag(Kontantstøtte, 500.0, Periode(1.januar(2020), 31.januar(2020)))

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode
        ).let {
            it.size shouldBe 12
            it[Periode(1.januar(2020), 31.januar(2020))]!! shouldContainAll listOf(
                expectedArbeidsinntekt,
                expectedKontantstøtte
            )
            it.values.forEach { it.none { it.getFradragstype() == ForventetInntekt } }
        }
    }

    @Test
    fun `velger forventet inntekt dersom den er større enn arbeidsinntekt`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 500.0, periode)
        val kontantstøtte = lagFradrag(Kontantstøtte, 500.0, periode)
        val forventetInntekt = lagFradrag(ForventetInntekt, 2000.0, periode)

        val expectedForventetInntekt =
            lagPeriodisertFradrag(ForventetInntekt, 2000.0, Periode(1.januar(2020), 31.januar(2020)))
        val expectedKontantstøtte =
            lagPeriodisertFradrag(Kontantstøtte, 500.0, Periode(1.januar(2020), 31.januar(2020)))

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode
        ).let {
            it.size shouldBe 12
            it[Periode(1.januar(2020), 31.januar(2020))]!! shouldContainAll listOf(
                expectedForventetInntekt,
                expectedKontantstøtte
            )
            it.values.forEach { it.none { it.getFradragstype() == Arbeidsinntekt } }
        }
    }

    @Test
    fun `tar med fradrag som tilhører EPS`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val epsArbeidsinntekt = lagFradrag(Arbeidsinntekt, 2000.0, periode, tilhører = EPS)
        val forventetInntekt = lagFradrag(ForventetInntekt, 1000.0, periode)

        val expectedBrukerInntekt =
            lagPeriodisertFradrag(ForventetInntekt, 1000.0, Periode(1.januar(2020), 31.januar(2020)))
        val expectedEpsInntekt = lagPeriodisertFradrag(
            BeregnetFradragEPS, 2000.0, Periode(1.januar(2020), 31.januar(2020)), EPS
        )

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(epsArbeidsinntekt, forventetInntekt),
            beregningsperiode = periode
        ).let {
            it.size shouldBe 12
            it[Periode(1.januar(2020), 31.januar(2020))]!! shouldBe listOf(
                expectedBrukerInntekt,
                expectedEpsInntekt
            )
            it.values.forEach { it.any { it.getTilhører() == BRUKER } shouldBe true }
            it.values.forEach { it.any { it.getTilhører() == EPS } shouldBe true }
        }
    }

    @Test
    fun `inneholder bare ett fradrag for eps, uavhengig av hvor mange som er input`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val forventetInntekt = lagFradrag(ForventetInntekt, 10000.0, periode)
        val epsForventetInntekt = lagFradrag(ForventetInntekt, 150000.0, periode, tilhører = EPS)
        val epsUføretrygd = lagFradrag(NAVytelserTilLivsopphold, 150000.0, periode, tilhører = EPS)
        val epsArbeidsinntekt = lagFradrag(Arbeidsinntekt, 5000.0, periode, tilhører = EPS)
        val epsKapitalinntekt = lagFradrag(Kapitalinntekt, 60000.0, periode, tilhører = EPS)
        val epsPensjon = lagFradrag(PrivatPensjon, 15000.0, periode, tilhører = EPS)

        FradragStrategy.EpsUnder67År.beregn(
            fradrag = listOf(
                forventetInntekt,
                epsForventetInntekt,
                epsUføretrygd,
                epsArbeidsinntekt,
                epsKapitalinntekt,
                epsPensjon
            ),
            beregningsperiode = periode
        ).let { fradrag ->
            fradrag.size shouldBe 12
            fradrag.values.forEach { alleFradrag ->
                alleFradrag.filter { it.getTilhører() == EPS }.let { epsFradrag ->
                    epsFradrag shouldHaveSize 1
                    epsFradrag.all { it.getFradragstype() == BeregnetFradragEPS } shouldBe true
                }
            }
        }
    }
}
