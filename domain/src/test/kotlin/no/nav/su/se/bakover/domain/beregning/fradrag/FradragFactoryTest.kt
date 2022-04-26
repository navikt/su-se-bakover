package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.test.månedsperiodeApril2020
import no.nav.su.se.bakover.test.månedsperiodeFebruar2020
import no.nav.su.se.bakover.test.månedsperiodeJanuar2020
import no.nav.su.se.bakover.test.månedsperiodeMars2020
import org.junit.jupiter.api.Test

internal class FradragFactoryTest {
    @Test
    fun `periodiserer fradrag for enkel måned`() {
        val f1 = IkkePeriodisertFradrag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = månedsperiodeJanuar2020,
            tilhører = FradragTilhører.BRUKER,
        )
        val periodisert = FradragForMåned(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            måned = månedsperiodeJanuar2020,
            tilhører = FradragTilhører.BRUKER,
        )
        FradragFactory.periodiser(f1) shouldBe listOf(periodisert)
    }

    @Test
    fun `periodiserer fradrag for flere måneder`() {
        val f1 = IkkePeriodisertFradrag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = Periode.create(1.januar(2020), 30.april(2020)),
            tilhører = FradragTilhører.BRUKER
        )
        FradragFactory.periodiser(f1) shouldBe listOf(
            FradragForMåned(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                måned = månedsperiodeJanuar2020,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragForMåned(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                måned = månedsperiodeFebruar2020,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragForMåned(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                måned = månedsperiodeMars2020,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragForMåned(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                måned = månedsperiodeApril2020,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `et periodisert fradrag som periodiseres er lik seg selv`() {
        val f1 = FradragForMåned(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            måned = månedsperiodeJanuar2020,
            tilhører = FradragTilhører.BRUKER,
        )
        FradragFactory.periodiser(f1) shouldBe listOf(f1)
    }
}
