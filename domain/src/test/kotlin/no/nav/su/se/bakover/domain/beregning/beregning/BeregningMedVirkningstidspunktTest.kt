package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.beregning.BeregningMedVirkningstidspunkt
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.IkkePeriodisertFradrag
import org.junit.jupiter.api.Test

internal class BeregningMedVirkningstidspunktTest {

    @Test
    fun `summer for enkel beregning med virk`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )
        beregning.getSumYtelse() shouldBe 250116
        beregning.getSumFradrag() shouldBe 0
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `fradrag for alle perioder med virk`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 238116
        beregning.getSumFradrag() shouldBe 12000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (19637 to 1000.0)),
                (februar(2020) to (19637 to 1000.0)),
                (mars(2020) to (19637 to 1000.0)),
                (april(2020) to (19637 to 1000.0)),
                (mai(2020) to (19946 to 1000.0)),
                (juni(2020) to (19946 to 1000.0)),
                (juli(2020) to (19946 to 1000.0)),
                (august(2020) to (19946 to 1000.0)),
                (september(2020) to (19946 to 1000.0)),
                (oktober(2020) to (19946 to 1000.0)),
                (november(2020) to (19946 to 1000.0)),
                (desember(2020) to (19946 to 1000.0)),
            ),
        )
    }

    @Test
    fun `g-regulering skal tre i kraft selv om reguleringsmåned ikke har 10 prosent endring`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )
        beregning.getSumYtelse() shouldBe 250116
        beregning.getSumFradrag() shouldBe 0
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `g-regulering skal tre i kraft selv om reguleringsmåned har positiv 10 prosent endring`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = Periode.create(1.januar(2020), 30.april(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 7000.0,
                    periode = Periode.create(1.mai(2020), 31.desember(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )
        beregning.getSumYtelse() shouldBe 154116
        beregning.getSumFradrag() shouldBe 96000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (10637 to 10000.0)),
                (februar(2020) to (10637 to 10000.0)),
                (mars(2020) to (10637 to 10000.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (13946 to 7000.0)),
                (juni(2020) to (13946 to 7000.0)),
                (juli(2020) to (13946 to 7000.0)),
                (august(2020) to (13946 to 7000.0)),
                (september(2020) to (13946 to 7000.0)),
                (oktober(2020) to (13946 to 7000.0)),
                (november(2020) to (13946 to 7000.0)),
                (desember(2020) to (13946 to 7000.0)),
            ),
        )
    }

    @Test
    fun `g-regulering skal tre i kraft selv om reguleringsmåned har negativ 10 prosent endring`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = Periode.create(1.januar(2020), 30.april(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 13000.0,
                    periode = Periode.create(1.mai(2020), 31.desember(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )
        beregning.getSumYtelse() shouldBe 109116
        beregning.getSumFradrag() shouldBe 141000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (10637 to 10000.0)),
                (februar(2020) to (10637 to 10000.0)),
                (mars(2020) to (10637 to 10000.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (10946 to 10000.0)),
                (juni(2020) to (7946 to 13000.0)),
                (juli(2020) to (7946 to 13000.0)),
                (august(2020) to (7946 to 13000.0)),
                (september(2020) to (7946 to 13000.0)),
                (oktober(2020) to (7946 to 13000.0)),
                (november(2020) to (7946 to 13000.0)),
                (desember(2020) to (7946 to 13000.0)),
            ),
        )
    }

    @Test
    fun `fradrag for ensom måned med virk`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = april(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 250116
        beregning.getSumFradrag() shouldBe 0
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `fradrag for to påfølgende måneder med virk`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = Periode.create(1.mars(2020), 30.april(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 245116
        beregning.getSumFradrag() shouldBe 5000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (15637 to 5000.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `fradrag for annenhver måneder med virk`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = februar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = april(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = juni(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = august(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = oktober(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 250116
        beregning.getSumFradrag() shouldBe 0
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `inntekter øker i påfølgende`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = mars(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = april(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 3000.0,
                    periode = mai(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 4000.0,
                    periode = juni(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = juli(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 6000.0,
                    periode = august(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 7000.0,
                    periode = september(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 8000.0,
                    periode = oktober(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 227116
        beregning.getSumFradrag() shouldBe 23000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (17946 to 3000.0)),
                (juli(2020) to (17946 to 3000.0)),
                (august(2020) to (15946 to 5000.0)),
                (september(2020) to (15946 to 5000.0)),
                (oktober(2020) to (13946 to 7000.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `inntekter reduseres i påfølgende`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = januar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 8000.0,
                    periode = februar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 7000.0,
                    periode = mars(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 6000.0,
                    periode = april(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = mai(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 4000.0,
                    periode = juni(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = juli(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = august(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 218116
        beregning.getSumFradrag() shouldBe 32000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (18637 to 2000.0)),
                (februar(2020) to (18637 to 2000.0)),
                (mars(2020) to (12637 to 8000.0)),
                (april(2020) to (14637 to 6000.0)),
                (mai(2020) to (14946 to 6000.0)),
                (juni(2020) to (16946 to 4000.0)),
                (juli(2020) to (18946 to 2000.0)),
                (august(2020) to (18946 to 2000.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `økning og reduksjon`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = februar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = mars(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = april(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = mai(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 9000.0,
                    periode = juni(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 9000.0,
                    periode = juli(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 6000.0,
                    periode = august(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 7000.0,
                    periode = september(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 15000.0,
                    periode = oktober(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 192116
        beregning.getSumFradrag() shouldBe 58000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (10946 to 10000.0)),
                (juni(2020) to (10946 to 10000.0)),
                (juli(2020) to (10946 to 10000.0)),
                (august(2020) to (14946 to 6000.0)),
                (september(2020) to (14946 to 6000.0)),
                (oktober(2020) to (14946 to 6000.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `varig inntektsøkning`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = Periode.create(1.juli(2020), 31.desember(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 225116
        beregning.getSumFradrag() shouldBe 25000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (15946 to 5000.0)),
                (september(2020) to (15946 to 5000.0)),
                (oktober(2020) to (15946 to 5000.0)),
                (november(2020) to (15946 to 5000.0)),
                (desember(2020) to (15946 to 5000.0)),
            ),
        )
    }

    @Test
    fun `varig inntektsreduksjon`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = Periode.create(1.februar(2020), 31.mai(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = Periode.create(1.juni(2020), 31.desember(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 185116
        beregning.getSumFradrag() shouldBe 65000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (10637 to 10000.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (10946 to 10000.0)),
                (juni(2020) to (15946 to 5000.0)),
                (juli(2020) to (15946 to 5000.0)),
                (august(2020) to (15946 to 5000.0)),
                (september(2020) to (15946 to 5000.0)),
                (oktober(2020) to (15946 to 5000.0)),
                (november(2020) to (15946 to 5000.0)),
                (desember(2020) to (15946 to 5000.0)),
            ),
        )
    }

    @Test
    fun `klynger med inntekt`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = Periode.create(1.januar(2020), 29.februar(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = Periode.create(1.april(2020), 31.juli(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 3000.0,
                    periode = Periode.create(1.oktober(2020), 30.november(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 228116
        beregning.getSumFradrag() shouldBe 22000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (18637 to 2000.0)),
                (februar(2020) to (18637 to 2000.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (15946 to 5000.0)),
                (juni(2020) to (15946 to 5000.0)),
                (juli(2020) to (15946 to 5000.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (17946 to 3000.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }
}

private fun List<Månedsberegning>.assertMåneder(expected: Map<Periode, Pair<Int, Double>>) {
    map {
        Triple(
            first = it.periode,
            second = it.getSumYtelse(),
            third = it.getSumFradrag(),
        )
    }.forEach { (periode, ytelse, fradrag) ->
        expected[periode] shouldBe Pair(ytelse, fradrag)
    }
}
