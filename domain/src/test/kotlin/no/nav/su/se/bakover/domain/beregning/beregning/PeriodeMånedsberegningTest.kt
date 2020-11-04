package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.PeriodeFradrag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PeriodeMånedsberegningTest {
    @Test
    fun `summerer måned uten fradrag`() {
        val månedsberegning = MånedsberegningFactory.ny(
            periode = Periode(1.januar(2020), 31.januar(2020)),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
        månedsberegning.sum() shouldBe 20637.32
        månedsberegning.fradrag() shouldBe 0
    }

    @Test
    fun `summerer måned med fradrag`() {
        val månedsberegning = MånedsberegningFactory.ny(
            periode = Periode(1.januar(2020), 31.januar(2020)),
            sats = Sats.HØY,
            fradrag = listOf(
                PeriodeFradrag(
                    type = Fradragstype.Kontantstøtte,
                    beløp = 5000.0,
                    periode = Periode(1.januar(2020), 31.januar(2020))
                )
            )
        )
        månedsberegning.sum() shouldBe 15637.32
        månedsberegning.fradrag() shouldBe 5000
    }

    @Test
    fun `godtar ikke fradrag fra andre måneder`() {
        assertThrows<IllegalArgumentException> {
            MånedsberegningFactory.ny(
                periode = Periode(1.januar(2020), 31.januar(2020)),
                sats = Sats.HØY,
                fradrag = listOf(
                    PeriodeFradrag(
                        type = Fradragstype.Kontantstøtte,
                        beløp = 5000.0,
                        periode = Periode(1.desember(2020), 31.desember(2020))
                    )
                )
            )
        }
    }

    @Test
    fun `tillater bare beregning av en måned av gangen`() {
        assertThrows<IllegalArgumentException> {
            MånedsberegningFactory.ny(
                periode = Periode(1.januar(2020), 31.mars(2020)),
                sats = Sats.HØY,
                fradrag = emptyList()
            )
        }
    }

    @Test
    fun `sum kan ikke bli mindre enn 0`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
        val månedsberegning = MånedsberegningFactory.ny(
            periode = periode,
            sats = Sats.ORDINÆR,
            fradrag = listOf(
                PeriodeFradrag(
                    type = Fradragstype.Kontantstøtte,
                    beløp = 123000.0,
                    periode = periode
                )
            )
        )
        månedsberegning.sum() shouldBe 0
    }

    @Test
    fun `fradrag kan ikke overstige satsbeløpet`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
        val månedsberegning = MånedsberegningFactory.ny(
            periode = periode,
            sats = Sats.ORDINÆR,
            fradrag = listOf(
                PeriodeFradrag(
                    type = Fradragstype.Kontantstøtte,
                    beløp = 123000.0,
                    periode = periode
                )
            )
        )
        månedsberegning.sum() shouldBe 0
        månedsberegning.fradrag() shouldBe 18973.02
    }

    @Test
    fun `henter aktuelt grunnbeløp for periode`() {
        val m1 = MånedsberegningFactory.ny(
            periode = Periode(1.januar(2020), 31.januar(2020)),
            sats = Sats.ORDINÆR,
            fradrag = emptyList()
        )
        m1.grunnbeløp() shouldBe 99858

        val m2 = MånedsberegningFactory.ny(
            periode = Periode(1.desember(2020), 31.desember(2020)),
            sats = Sats.ORDINÆR,
            fradrag = emptyList()
        )
        m2.grunnbeløp() shouldBe 101351
    }
}
