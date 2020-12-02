package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class IkkePeriodisertFradragTest {

    @Test
    fun `kan ikke opprette fradrag med negative beløp`() {
        assertThrows<IllegalArgumentException> {
            FradragFactory.ny(
                type = Fradragstype.Arbeidsinntekt,
                beløp = -5.0,
                periode = Periode(1.januar(2020), 31.januar(2020)),
                tilhører = FradragTilhører.BRUKER
            )
        }
    }

    @Test
    fun `summerer beløp for måned og total`() {
        val f1 = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            beløp = 12000.0,
            periode = Periode(1.januar(2020), 31.januar(2020)),
            tilhører = FradragTilhører.BRUKER
        )
        f1.getTotaltFradrag() shouldBe 12000.0
        f1.getFradragPerMåned() shouldBe 12000.0

        val f2 = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            beløp = 12000.0,
            periode = Periode(1.januar(2020), 31.desember(2020)),
            tilhører = FradragTilhører.BRUKER
        )
        f2.getTotaltFradrag() shouldBe 12000.0
        f2.getFradragPerMåned() shouldBe 1000.0
    }

    @Test
    fun `periodisering av ikke periodisert fradrag for enkeltmåned er det samme som periodisert fradrag for samme måned`() {
        val f1 = IkkePeriodisertFradrag(
            type = Fradragstype.Arbeidsinntekt,
            beløp = 12000.0,
            periode = Periode(1.januar(2020), 31.januar(2020)),
            tilhører = FradragTilhører.BRUKER
        )
        val periodisert = PeriodisertFradrag(
            type = Fradragstype.Arbeidsinntekt,
            beløp = 12000.0,
            periode = Periode(1.januar(2020), 31.januar(2020)),
            tilhører = FradragTilhører.BRUKER
        )
        FradragFactory.periodiser(f1) shouldBe listOf(periodisert)
    }
}
