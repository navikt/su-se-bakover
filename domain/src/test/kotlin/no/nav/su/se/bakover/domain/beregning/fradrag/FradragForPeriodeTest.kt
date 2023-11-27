package no.nav.su.se.bakover.domain.beregning.fradrag

import behandling.domain.beregning.fradrag.Fradragstype
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class FradragForPeriodeTest {

    @Test
    fun `kan ikke opprette fradrag med negative beløp`() {
        assertThrows<IllegalArgumentException> {
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Arbeidsinntekt,
                månedsbeløp = -5.0,
                periode = januar(2020),
                tilhører = FradragTilhører.BRUKER,
            )
        }
    }

    @Test
    fun `summerer beløp for måned og total`() {
        val f1 = FradragFactory.nyFradragsperiode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = januar(2020),
            tilhører = FradragTilhører.BRUKER,
        )
        f1.månedsbeløp shouldBe 12000.0

        val f2 = FradragFactory.nyFradragsperiode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = år(2020),
            tilhører = FradragTilhører.BRUKER,
        )
        f2.månedsbeløp shouldBe 12000.0
    }

    @Test
    fun `periodisering av ikke periodisert fradrag for enkeltmåned er det samme som periodisert fradrag for samme måned`() {
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
    fun `kopi bevarer original periode dersom denne er inneholdt i maksimal periode`() {
        val fradrag = FradragForPeriode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = januar(2021),
            tilhører = FradragTilhører.BRUKER,
        )

        fradrag.copy(CopyArgs.Snitt(år(2021))) shouldBe fradrag
    }

    @Test
    fun `kopi justerer original periode dersom denne inneholder maksimal periode`() {
        val fradrag = FradragForPeriode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = år(2021),
            tilhører = FradragTilhører.BRUKER,
        )
        fradrag.copy(CopyArgs.Snitt(Periode.create(1.mars(2021), 31.juli(2021)))) shouldBe fradrag.copy(
            periode = Periode.create(1.mars(2021), 31.juli(2021)),
        )
    }

    @Test
    fun `returnerer ingenting dersom original periode er utenfor maksimal periode`() {
        val fradrag = FradragForPeriode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = januar(2021),
            tilhører = FradragTilhører.BRUKER,
        )
        fradrag.copy(CopyArgs.Snitt(Periode.create(1.februar(2021), 31.desember(2021)))) shouldBe null
    }

    @Test
    fun `kopi justerer original dersom fraOgMed er utenfor maksimal periode`() {
        val fradrag = FradragForPeriode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = Periode.create(1.januar(2021), 31.juli(2021)),
            tilhører = FradragTilhører.BRUKER,
        )
        fradrag.copy(CopyArgs.Snitt(Periode.create(1.februar(2021), 31.desember(2021)))) shouldBe fradrag.copy(
            periode = Periode.create(1.februar(2021), 31.juli(2021)),
        )
    }

    @Test
    fun `kopi justerer original dersom tilOgMed er utenfor maksimal periode`() {
        val fradrag = FradragForPeriode(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = Periode.create(1.juli(2021), 31.desember(2021)),
            tilhører = FradragTilhører.BRUKER,
        )
        fradrag.copy(CopyArgs.Snitt(Periode.create(1.januar(2021), 30.november(2021)))) shouldBe fradrag.copy(
            periode = Periode.create(1.juli(2021), 30.november(2021)),
        )
    }
}
