package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.PeriodisertBeregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.IkkePeriodisertFradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.PeriodisertFradrag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PeriodisertBeregningTest {
    @Test
    fun `summerer måned uten fradrag`() {
        val månedsberegning = MånedsberegningFactory.ny(
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            sats = Sats.HØY,
            fradrag = emptyList(),
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
                    type = Fradragstype.Kontantstøtte,
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
                        type = Fradragstype.Kontantstøtte,
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
                fradrag = emptyList(),
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
                    type = Fradragstype.Kontantstøtte,
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
                    type = Fradragstype.Kontantstøtte,
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
            fradrag = emptyList(),
        )
        m1.getBenyttetGrunnbeløp() shouldBe 99858

        val m2 = MånedsberegningFactory.ny(
            periode = Periode.create(1.desember(2020), 31.desember(2020)),
            sats = Sats.ORDINÆR,
            fradrag = emptyList(),
        )
        m2.getBenyttetGrunnbeløp() shouldBe 101351
    }

    @Test
    fun `henter fradrag for aktuell måned`() {
        val f1 = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1234.56,
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val m1 = MånedsberegningFactory.ny(
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            sats = Sats.ORDINÆR,
            fradrag = listOf(f1),
        )
        m1.getFradrag() shouldBe FradragFactory.periodiser(f1)
    }

    @Test
    fun `er fradrag for eps benyttet i beregning`() {
        val f1 = FradragFactory.ny(
            type = Fradragstype.BeregnetFradragEPS,
            månedsbeløp = 1234.56,
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )
        val m1 = MånedsberegningFactory.ny(
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            sats = Sats.ORDINÆR,
            fradrag = listOf(f1),
        )
        m1.erFradragForEpsBenyttetIBeregning() shouldBe true

        val f2 = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1234.56,
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val m2 = MånedsberegningFactory.ny(
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
            sats = Sats.ORDINÆR,
            fradrag = listOf(f2),
        )

        m2.erFradragForEpsBenyttetIBeregning() shouldBe false
    }

    @Test
    fun `kan forskyves n antall måneder`() {
        PeriodisertBeregning(
            periode = januar(2021),
            sats = Sats.HØY,
            fradrag = listOf(
                PeriodisertFradrag(
                    type = Fradragstype.PrivatPensjon,
                    månedsbeløp = 2000.0,
                    periode = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                PeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 7000.0,
                    periode = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
            fribeløpForEps = FradragStrategy.Enslig.getEpsFribeløp(januar(2020)),
        ).forskyv(1, FradragStrategy.Enslig) shouldBe PeriodisertBeregning(
            periode = februar(2021),
            sats = Sats.HØY,
            fradrag = listOf(
                PeriodisertFradrag(
                    type = Fradragstype.PrivatPensjon,
                    månedsbeløp = 2000.0,
                    periode = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                PeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 7000.0,
                    periode = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
            fribeløpForEps = FradragStrategy.Enslig.getEpsFribeløp(februar(2020)),
        )
    }

    @Test
    fun `legger til merknad for endret grunnbeløp hvis relevant`() {
        PeriodisertBeregning(
            periode = januar(2020),
            sats = Sats.HØY,
            fradrag = listOf(),
            fribeløpForEps = 0.0,
        ).getMerknader().filterIsInstance<Merknad.EndringGrunnbeløp>() shouldHaveSize 0

        PeriodisertBeregning(
            periode = mai(2021),
            sats = Sats.HØY,
            fradrag = listOf(),
            fribeløpForEps = 0.0,
        ).getMerknader().filterIsInstance<Merknad.EndringGrunnbeløp>() shouldBe listOf(
            Merknad.EndringGrunnbeløp(
                gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2020),
                    grunnbeløp = 101351,
                ),
                nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2021),
                    grunnbeløp = 106399,
                ),
            ),
        )

        PeriodisertBeregning(
            periode = januar(2020),
            sats = Sats.HØY,
            fradrag = listOf(),
            fribeløpForEps = 0.0,
        ).getMerknader().filterIsInstance<Merknad.EndringGrunnbeløp>() shouldHaveSize 0

        PeriodisertBeregning(
            periode = mai(2020),
            sats = Sats.HØY,
            fradrag = listOf(),
            fribeløpForEps = 0.0,
        ).getMerknader().filterIsInstance<Merknad.EndringGrunnbeløp>() shouldBe listOf(
            Merknad.EndringGrunnbeløp(
                gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2019),
                    grunnbeløp = 99858,
                ),
                nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2020),
                    grunnbeløp = 101351,
                ),
            ),
        )
    }
}
