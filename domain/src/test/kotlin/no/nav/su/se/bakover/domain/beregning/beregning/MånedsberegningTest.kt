package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class MånedsberegningTest {
    @Test
    fun `summerer måned uten fradrag`() {
        val månedsberegning = Månedsberegning(
            periode = Periode(1.januar(2020), 31.januar(2020)),
            satsbeløp = 1000.0,
            fradrag = emptyList()
        )
        månedsberegning.sum() shouldBe 1000
        månedsberegning.fradrag() shouldBe 0
    }

    @Test
    fun `summerer måned med fradrag`() {
        val månedsberegning = Månedsberegning(
            periode = Periode(1.januar(2020), 31.januar(2020)),
            satsbeløp = 10000.0,
            fradrag = listOf(
                Fradrag(
                    type = Fradragstype.Kontantstøtte,
                    beløp = 5000.0,
                    periode = Periode(1.januar(2020), 31.januar(2020))
                )
            )
        )
        månedsberegning.sum() shouldBe 5000
        månedsberegning.fradrag() shouldBe 5000
    }

    @Test
    fun `godtar ikke fradrag fra andre måneder`() {
        assertThrows<IllegalArgumentException> {
            Månedsberegning(
                periode = Periode(1.januar(2020), 31.januar(2020)),
                satsbeløp = 10000.0,
                fradrag = listOf(
                    Fradrag(
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
            Månedsberegning(
                periode = Periode(1.januar(2020), 31.mars(2020)),
                satsbeløp = 1000.0,
                fradrag = emptyList()
            )
        }
    }

    @Test
    fun `sum kan ikke bli mindre enn 0`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
        val månedsberegning = Månedsberegning(
            periode = periode,
            satsbeløp = Sats.ORDINÆR.månedsbeløp(1.januar(2020)),
            fradrag = listOf(
                Fradrag(
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
        val månedsberegning = Månedsberegning(
            periode = periode,
            satsbeløp = 18973.0,
            fradrag = listOf(
                Fradrag(
                    type = Fradragstype.Kontantstøtte,
                    beløp = 123000.0,
                    periode = periode
                )
            )
        )
        månedsberegning.sum() shouldBe 0
        månedsberegning.fradrag() shouldBe 18973
    }
}
