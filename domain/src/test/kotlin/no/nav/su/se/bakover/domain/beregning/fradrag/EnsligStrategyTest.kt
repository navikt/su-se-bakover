package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.test.månedsperiodeJanuar2020
import no.nav.su.se.bakover.test.månedsperiodeJuni2020
import org.junit.jupiter.api.Test

internal class EnsligStrategyTest {
    @Test
    fun `velger arbeidsinntekt dersom den er større enn forventet inntekt`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val arbeidsinntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt), 2000.0, periode)
        val kontantstøtte = lagFradrag(FradragskategoriWrapper(Fradragskategori.Kontantstøtte), 500.0, periode)
        val forventetInntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 500.0, periode)

        val expectedArbeidsinntekt =
            lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt), 2000.0, månedsperiodeJanuar2020)
        val expectedKontantstøtte =
            lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.Kontantstøtte), 500.0, månedsperiodeJanuar2020)

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[månedsperiodeJanuar2020]!! shouldContainAll listOf(
                expectedArbeidsinntekt,
                expectedKontantstøtte,
            )
            it.values.forEach { it.none { it.fradragskategoriWrapper.kategori == Fradragskategori.ForventetInntekt } }
        }
    }

    @Test
    fun `velger forventet inntekt dersom den er større enn arbeidsinntekt`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val arbeidsinntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt), 500.0, periode)
        val kontantstøtte = lagFradrag(FradragskategoriWrapper(Fradragskategori.Kontantstøtte), 500.0, periode)
        val forventetInntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 2000.0, periode)

        val expectedForventetInntekt =
            lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 2000.0, månedsperiodeJanuar2020)
        val expectedKontantstøtte =
            lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.Kontantstøtte), 500.0, månedsperiodeJanuar2020)

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[månedsperiodeJanuar2020]!! shouldContainAll listOf(
                expectedForventetInntekt,
                expectedKontantstøtte,
            )
            it.values.forEach { it.none { it.fradragskategoriWrapper.kategori == Fradragskategori.Arbeidsinntekt } }
        }
    }

    @Test
    fun `bruker bare fradrag som tilhører bruker`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val arbeidsinntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt), 5000.0, periode, tilhører = FradragTilhører.EPS)
        val kontantstøtte = lagFradrag(FradragskategoriWrapper(Fradragskategori.Kontantstøtte), 5000.0, periode)
        val forventetInntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 15000.0, periode)

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntekt, kontantstøtte, forventetInntekt),
            beregningsperiode = periode
        ).let {
            it.values.forEach { it.none { it.tilhører == FradragTilhører.EPS } }
        }
    }

    @Test
    fun `varierer bruk av arbeidsinntekt og forventet inntekt for forskjellige måneder`() {
        val arbeidsinntektJanuar = lagFradrag(FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt), 6000.0, månedsperiodeJanuar2020)
        val arbeidsinntektJuni = lagFradrag(FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt), 1000.0, månedsperiodeJuni2020)
        val forventetInntekt = lagFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 2000.0, Periode.create(1.januar(2020), 31.desember(2020)))

        val expectedInntektJanuar =
            lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt), 6000.0, månedsperiodeJanuar2020)
        val expectedInntektJuni =
            lagPeriodisertFradrag(FradragskategoriWrapper(Fradragskategori.ForventetInntekt), 2000.0, månedsperiodeJuni2020)

        FradragStrategy.Enslig.beregn(
            fradrag = listOf(arbeidsinntektJanuar, forventetInntekt, arbeidsinntektJuni),
            beregningsperiode = Periode.create(1.januar(2020), 31.desember(2020)),
        ).let {
            it[månedsperiodeJanuar2020]!! shouldBe (listOf(expectedInntektJanuar))
            it[månedsperiodeJuni2020]!! shouldBe (listOf(expectedInntektJuni))
            it.values.forEach { it shouldHaveSize 1 }
        }
    }
}
