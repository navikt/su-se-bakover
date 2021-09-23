package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PeriodisertFradragTest {
    @Test
    fun `periodiserte fradrag kan kun opprettes for en enkelt måned`() {
        assertThrows<IllegalArgumentException> {
            PeriodisertFradrag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 2000.0,
                periode = Periode.create(1.januar(2020), 30.april(2020)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            )
        }
    }

    @Test
    fun `periodisert fradrag er likt seg selv dersom det periodiseres på nytt`() {
        val fradrag = PeriodisertFradrag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 2000.0,
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        FradragFactory.periodiser(fradrag) shouldBe listOf(fradrag)

        val ikkePeriodisert = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = periode2021,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )

        val periodiserteFradrag = FradragFactory.periodiser(ikkePeriodisert)

        periodiserteFradrag shouldHaveSize 12

        periodiserteFradrag.flatMap { FradragFactory.periodiser(it) } shouldBe periodiserteFradrag
    }

    @Test
    fun `kan forskyves n antall måneder`() {
        PeriodisertFradrag(
            type = Fradragstype.PrivatPensjon,
            månedsbeløp = 2000.0,
            periode = januar(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        ).forskyv(1) shouldBe PeriodisertFradrag(
            type = Fradragstype.PrivatPensjon,
            månedsbeløp = 2000.0,
            periode = februar(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        PeriodisertFradrag(
            type = Fradragstype.PrivatPensjon,
            månedsbeløp = 2000.0,
            periode = januar(2021),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        ).forskyv(-12) shouldBe PeriodisertFradrag(
            type = Fradragstype.PrivatPensjon,
            månedsbeløp = 2000.0,
            periode = januar(2020),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
    }
}
