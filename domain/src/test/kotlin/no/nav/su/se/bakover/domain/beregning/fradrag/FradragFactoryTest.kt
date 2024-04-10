package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragForPeriode
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype

internal class FradragFactoryTest {
    @Test
    fun `periodiserer fradrag for enkel måned`() {
        val f1 = FradragForPeriode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = januar(2020),
            tilhører = FradragTilhører.BRUKER,
        )
        val periodisert = FradragForMåned(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            måned = januar(2020),
            tilhører = FradragTilhører.BRUKER,
        )
        FradragFactory.periodiser(f1) shouldBe listOf(periodisert)
    }

    @Test
    fun `periodiserer fradrag for flere måneder`() {
        val f1 = FradragForPeriode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = Periode.create(1.januar(2020), 30.april(2020)),
            tilhører = FradragTilhører.BRUKER,
        )
        FradragFactory.periodiser(f1) shouldBe listOf(
            FradragForMåned(
                fradragstype = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                måned = januar(2020),
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragForMåned(
                fradragstype = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                måned = februar(2020),
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragForMåned(
                fradragstype = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                måned = mars(2020),
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragForMåned(
                fradragstype = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 12000.0,
                måned = april(2020),
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `et periodisert fradrag som periodiseres er lik seg selv`() {
        val f1 = FradragForMåned(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            måned = januar(2020),
            tilhører = FradragTilhører.BRUKER,
        )
        FradragFactory.periodiser(f1) shouldBe listOf(f1)
    }
}
