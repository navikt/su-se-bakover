package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.IkkePeriodisertFradrag
import no.nav.su.se.bakover.domain.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PeriodisertBeregningTest {
    @Test
    fun `summerer måned uten fradrag`() {
        val månedsberegning = MånedsberegningFactory.ny(
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
        månedsberegning.getSumYtelse() shouldBe 20637
        månedsberegning.getSumFradrag() shouldBe 0
    }

    @Test
    fun `summerer måned med fradrag`() {
        val månedsberegning = MånedsberegningFactory.ny(
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    opprettet = fixedTidspunkt,
                    fradragstype = Fradragstype.Kontantstøtte,
                    månedsbeløp = 5000.0,
                    periode = Periode.create(1.januar(2020), 31.januar(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )
        månedsberegning.getSumYtelse() shouldBe 15637
        månedsberegning.getSumFradrag() shouldBe 5000
    }

    @Test
    fun `godtar ikke fradrag fra andre måneder`() {
        assertThrows<IllegalArgumentException> {
            MånedsberegningFactory.ny(
                periode = Periode.create(1.januar(2020), 31.januar(2020)),
                sats = Sats.HØY,
                fradrag = listOf(
                    IkkePeriodisertFradrag(
                        opprettet = fixedTidspunkt,
                        fradragstype = Fradragstype.Kontantstøtte,
                        månedsbeløp = 5000.0,
                        periode = Periode.create(1.desember(2020), 31.desember(2020)),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            )
        }
    }

    @Test
    fun `tillater bare beregning av en måned av gangen`() {
        assertThrows<IllegalArgumentException> {
            MånedsberegningFactory.ny(
                periode = Periode.create(1.januar(2020), 31.mars(2020)),
                sats = Sats.HØY,
                fradrag = emptyList()
            )
        }
    }

    @Test
    fun `sum kan ikke bli mindre enn 0`() {
        val periode = Periode.create(1.januar(2020), 31.januar(2020))
        val månedsberegning = MånedsberegningFactory.ny(
            periode = periode,
            sats = Sats.ORDINÆR,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    opprettet = fixedTidspunkt,
                    fradragstype = Fradragstype.Kontantstøtte,
                    månedsbeløp = 123000.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )
        månedsberegning.getSumYtelse() shouldBe 0
    }

    @Test
    fun `fradrag kan ikke overstige satsbeløpet`() {
        val periode = Periode.create(1.januar(2020), 31.januar(2020))
        val månedsberegning = MånedsberegningFactory.ny(
            periode = periode,
            sats = Sats.ORDINÆR,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    opprettet = fixedTidspunkt,
                    fradragstype = Fradragstype.Kontantstøtte,
                    månedsbeløp = 123000.0,
                    periode = periode,
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
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            sats = Sats.ORDINÆR,
            fradrag = emptyList()
        )
        m1.getBenyttetGrunnbeløp() shouldBe 99858

        val m2 = MånedsberegningFactory.ny(
            periode = Periode.create(1.desember(2020), 31.desember(2020)),
            sats = Sats.ORDINÆR,
            fradrag = emptyList()
        )
        m2.getBenyttetGrunnbeløp() shouldBe 101351
    }

    @Test
    fun `henter fradrag for aktuell måned`() {
        val f1 = FradragFactory.ny(
            opprettet = fixedTidspunkt,
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1234.56,
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val m1 = MånedsberegningFactory.ny(
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            sats = Sats.ORDINÆR,
            fradrag = listOf(f1)
        )
        m1.getFradrag() shouldBe listOf(f1)
    }

    @Test
    fun `er fradrag for eps benyttet i beregning`() {
        val f1 = FradragFactory.ny(
            opprettet = fixedTidspunkt,
            type = Fradragstype.BeregnetFradragEPS,
            månedsbeløp = 1234.56,
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )
        val m1 = MånedsberegningFactory.ny(
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            sats = Sats.ORDINÆR,
            fradrag = listOf(f1)
        )
        m1.erFradragForEpsBenyttetIBeregning() shouldBe true

        val f2 = FradragFactory.ny(
            opprettet = fixedTidspunkt,
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1234.56,
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val m2 = MånedsberegningFactory.ny(
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            sats = Sats.ORDINÆR,
            fradrag = listOf(f2)
        )

        m2.erFradragForEpsBenyttetIBeregning() shouldBe false
    }
}
