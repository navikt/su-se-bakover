package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForMåned
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.lagFradrag
import no.nav.su.se.bakover.test.satsFactoryTest
import org.junit.jupiter.api.Test

internal class PeriodisertBeregningTest {

    private val forventetInntekt = lagFradrag(
        type = Fradragstype.ForventetInntekt,
        beløp = 0.0,
        periode = Periode.create(1.januar(2020), 31.desember(2022)),
        tilhører = FradragTilhører.BRUKER,
    )

    @Test
    fun `summerer måned uten fradrag`() {
        val månedsberegning = MånedsberegningFactory.ny(
            måned = januar(2020),
            strategy = BeregningStrategy.BorAlene(satsFactoryTest),
            fradrag = listOf(forventetInntekt),
        )
        månedsberegning.getSumYtelse() shouldBe 20637
        månedsberegning.getSumFradrag() shouldBe 0
    }

    @Test
    fun `summerer måned med fradrag`() {
        val månedsberegning = MånedsberegningFactory.ny(
            måned = januar(2020),
            strategy = BeregningStrategy.BorAlene(satsFactoryTest),
            fradrag = listOf(
                forventetInntekt,
                FradragForMåned(
                    fradragstype = Fradragstype.Kontantstøtte,
                    månedsbeløp = 5000.0,
                    måned = januar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )
        månedsberegning.getSumYtelse() shouldBe 15637
        månedsberegning.getSumFradrag() shouldBe 5000
    }

    @Test
    fun `sum kan ikke bli mindre enn 0`() {
        val periode = januar(2020)
        val månedsberegning = MånedsberegningFactory.ny(
            måned = periode,
            strategy = BeregningStrategy.BorAlene(satsFactoryTest),
            fradrag = listOf(
                forventetInntekt,
                FradragForMåned(
                    fradragstype = Fradragstype.Kontantstøtte,
                    månedsbeløp = 123000.0,
                    måned = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )
        månedsberegning.getSumYtelse() shouldBe 0
    }

    @Test
    fun `fradrag kan ikke overstige satsbeløpet`() {
        val periode = januar(2020)
        val månedsberegning = MånedsberegningFactory.ny(
            måned = periode,
            strategy = BeregningStrategy.BorMedVoksne(satsFactoryTest),
            fradrag = listOf(
                forventetInntekt,
                FradragForMåned(
                    fradragstype = Fradragstype.Kontantstøtte,
                    månedsbeløp = 123000.0,
                    måned = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )
        månedsberegning.getSumYtelse() shouldBe 0
        månedsberegning.getSumFradrag() shouldBe 18973.02
    }

    @Test
    fun `henter aktuelt grunnbeløp for periode`() {
        val m1 = MånedsberegningFactory.ny(
            måned = januar(2020),
            strategy = BeregningStrategy.BorMedVoksne(satsFactoryTest),
            fradrag = listOf(forventetInntekt),
        )
        m1.getBenyttetGrunnbeløp() shouldBe 99858

        val m2 = MånedsberegningFactory.ny(
            måned = desember(2020),
            strategy = BeregningStrategy.BorMedVoksne(satsFactoryTest),
            fradrag = listOf(forventetInntekt),
        )
        m2.getBenyttetGrunnbeløp() shouldBe 101351
    }

    @Test
    fun `henter fradrag for aktuell måned`() {
        val f1 = FradragFactory.nyMånedsperiode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1234.56,
            måned = januar(2020),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val m1 = MånedsberegningFactory.ny(
            måned = januar(2020),
            strategy = BeregningStrategy.BorMedVoksne(satsFactoryTest),
            fradrag = listOf(
                forventetInntekt,
                f1,
            ),
        )
        m1.getFradrag() shouldBe listOf(f1)
    }

    @Test
    fun `er fradrag for eps benyttet i beregning`() {
        val f1 = FradragFactory.nyMånedsperiode(
            fradragstype = Fradragstype.BeregnetFradragEPS,
            månedsbeløp = 1234.56,
            måned = januar(2020),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )
        val m1 = MånedsberegningFactory.ny(
            måned = januar(2020),
            strategy = BeregningStrategy.EpsUnder67År(satsFactoryTest),
            fradrag = listOf(
                forventetInntekt,
                f1,
            ),
        )
        m1.erFradragForEpsBenyttetIBeregning() shouldBe true

        val f2 = FradragFactory.nyMånedsperiode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1234.56,
            måned = januar(2020),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val m2 = MånedsberegningFactory.ny(
            måned = januar(2020),
            strategy = BeregningStrategy.BorMedVoksne(satsFactoryTest),
            fradrag = listOf(
                forventetInntekt,
                f2,
            ),
        )

        m2.erFradragForEpsBenyttetIBeregning() shouldBe false
    }
}
