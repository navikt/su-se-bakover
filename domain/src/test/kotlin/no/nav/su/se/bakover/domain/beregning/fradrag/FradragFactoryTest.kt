package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test

internal class FradragFactoryTest {
    @Test
    fun `periodiserer fradrag for enkel måned`() {
        val f1 = IkkePeriodisertFradrag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = Periode(1.januar(2020), 31.januar(2020)),
            tilhører = FradragTilhører.BRUKER
        )
        val periodisert = PeriodisertFradrag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = Periode(1.januar(2020), 31.januar(2020)),
            tilhører = FradragTilhører.BRUKER
        )
        FradragFactory.periodiser(f1) shouldBe listOf(periodisert)
    }

    @Test
    fun `periodiserer fradrag for flere måneder`() {
        val f1 = IkkePeriodisertFradrag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = Periode(1.januar(2020), 30.april(2020)),
            tilhører = FradragTilhører.BRUKER
        )
        FradragFactory.periodiser(f1) shouldBe listOf(
            PeriodisertFradrag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                periode = Periode(1.januar(2020), 31.januar(2020)),
                tilhører = FradragTilhører.BRUKER
            ),
            PeriodisertFradrag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                periode = Periode(1.februar(2020), 29.februar(2020)),
                tilhører = FradragTilhører.BRUKER
            ),
            PeriodisertFradrag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                periode = Periode(1.mars(2020), 31.mars(2020)),
                tilhører = FradragTilhører.BRUKER
            ),
            PeriodisertFradrag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                periode = Periode(1.april(2020), 30.april(2020)),
                tilhører = FradragTilhører.BRUKER
            )
        )
    }

    @Test
    fun `et periodisert fradrag som periodiseres er lik seg selv`() {
        val f1 = PeriodisertFradrag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = Periode(1.januar(2020), 31.januar(2020)),
            tilhører = FradragTilhører.BRUKER
        )
        FradragFactory.periodiser(f1) shouldBe listOf(f1)
    }
}
