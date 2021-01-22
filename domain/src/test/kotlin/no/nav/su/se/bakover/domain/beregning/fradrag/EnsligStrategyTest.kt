package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.Arbeidsinntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.ForventetInntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.Kontantstøtte
import org.junit.jupiter.api.Test

internal class EnsligStrategyTest {
    @Test
    fun `velger arbeidsinntekt dersom den er større enn forventet inntekt`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 2000.0, periode)
        val kontantstøtte = lagFradrag(Kontantstøtte, 500.0, periode)
        val forventetInntekt = lagFradrag(ForventetInntekt, 500.0, periode)

        val expectedArbeidsinntekt =
            lagPeriodisertFradrag(Arbeidsinntekt, 2000.0, Periode.create(1.januar(2020), 31.januar(2020)))
        val expectedKontantstøtte =
            lagPeriodisertFradrag(Kontantstøtte, 500.0, Periode.create(1.januar(2020), 31.januar(2020)))

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode
        ).let {
            it.size shouldBe 12
            it[Periode.create(1.januar(2020), 31.januar(2020))]!! shouldContainAll listOf(
                expectedArbeidsinntekt,
                expectedKontantstøtte
            )
            it.values.forEach { it.none { it.getFradragstype() == ForventetInntekt } }
        }
    }

    @Test
    fun `velger forventet inntekt dersom den er større enn arbeidsinntekt`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 500.0, periode)
        val kontantstøtte = lagFradrag(Kontantstøtte, 500.0, periode)
        val forventetInntekt = lagFradrag(ForventetInntekt, 2000.0, periode)

        val expectedForventetInntekt =
            lagPeriodisertFradrag(ForventetInntekt, 2000.0, Periode.create(1.januar(2020), 31.januar(2020)))
        val expectedKontantstøtte =
            lagPeriodisertFradrag(Kontantstøtte, 500.0, Periode.create(1.januar(2020), 31.januar(2020)))

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode
        ).let {
            it.size shouldBe 12
            it[Periode.create(1.januar(2020), 31.januar(2020))]!! shouldContainAll listOf(
                expectedForventetInntekt,
                expectedKontantstøtte
            )
            it.values.forEach { it.none { it.getFradragstype() == Arbeidsinntekt } }
        }
    }

    @Test
    fun `bruker bare fradrag som tilhører bruker`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 5000.0, periode, tilhører = FradragTilhører.EPS)
        val kontantstøtte = lagFradrag(Kontantstøtte, 5000.0, periode)
        val forventetInntekt = lagFradrag(ForventetInntekt, 15000.0, periode)

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode
        ).let {
            it.values.forEach { it.none { it.getTilhører() == FradragTilhører.EPS } }
        }
    }

    @Test
    fun `varierer bruk av arbeidsinntekt og forventet inntekt for forskjellige måneder`() {
        val arbeidsinntektJanuar = lagFradrag(Arbeidsinntekt, 6000.0, Periode.create(1.januar(2020), 31.januar(2020)))
        val arbeidsinntektJuni = lagFradrag(Arbeidsinntekt, 1000.0, Periode.create(1.juni(2020), 30.juni(2020)))
        val forventetInntekt = lagFradrag(ForventetInntekt, 2000.0, Periode.create(1.januar(2020), 31.desember(2020)))

        val expectedInntektJanuar =
            lagPeriodisertFradrag(Arbeidsinntekt, 6000.0, Periode.create(1.januar(2020), 31.januar(2020)))
        val expectedInntektJuni =
            lagPeriodisertFradrag(ForventetInntekt, 2000.0, Periode.create(1.juni(2020), 30.juni(2020)))

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntektJanuar, forventetInntekt, arbeidsinntektJuni),
            beregningsperiode = Periode.create(1.januar(2020), 31.desember(2020))
        ).let {
            it[Periode.create(1.januar(2020), 31.januar(2020))]!! shouldBe (listOf(expectedInntektJanuar))
            it[Periode.create(1.juni(2020), 30.juni(2020))]!! shouldBe (listOf(expectedInntektJuni))
            it.values.forEach { it shouldHaveSize 1 }
        }
    }
}
