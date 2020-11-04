package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PeriodeFradragTest {
    @Test
    fun `periodiserer fradrag for enkel måned`() {
        val f1 = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            beløp = 12000.0,
            periode = Periode(1.januar(2020), 31.januar(2020))
        )
        f1.periodiser() shouldBe listOf(f1)
    }

    @Test
    fun `periodiserer fradrag for flere måneder`() {
        val f1 = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            beløp = 12000.0,
            periode = Periode(1.januar(2020), 30.april(2020))
        )
        f1.periodiser() shouldBe listOf(
            PeriodeFradrag(
                type = Fradragstype.Arbeidsinntekt,
                beløp = 3000.0,
                periode = Periode(1.januar(2020), 31.januar(2020))
            ),
            PeriodeFradrag(
                type = Fradragstype.Arbeidsinntekt,
                beløp = 3000.0,
                periode = Periode(1.februar(2020), 29.februar(2020))
            ),
            PeriodeFradrag(
                type = Fradragstype.Arbeidsinntekt,
                beløp = 3000.0,
                periode = Periode(1.mars(2020), 31.mars(2020))
            ),
            PeriodeFradrag(
                type = Fradragstype.Arbeidsinntekt,
                beløp = 3000.0,
                periode = Periode(1.april(2020), 30.april(2020))
            )
        )
    }

    @Test
    fun `kan ikke opprette fradrag med negative beløp`() {
        assertThrows<IllegalArgumentException> {
            FradragFactory.ny(
                type = Fradragstype.Arbeidsinntekt,
                beløp = -5.0,
                periode = Periode(1.januar(2020), 31.januar(2020))
            )
        }
    }

    @Test
    fun `summerer beløp for måned og total`() {
        val f1 = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            beløp = 12000.0,
            periode = Periode(1.januar(2020), 31.januar(2020))
        )
        f1.totalBeløp() shouldBe 12000.0
        f1.månedsbeløp() shouldBe 12000.0

        val f2 = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            beløp = 12000.0,
            periode = Periode(1.januar(2020), 31.desember(2020))
        )
        f2.totalBeløp() shouldBe 12000.0
        f2.månedsbeløp() shouldBe 1000.0
    }
}
