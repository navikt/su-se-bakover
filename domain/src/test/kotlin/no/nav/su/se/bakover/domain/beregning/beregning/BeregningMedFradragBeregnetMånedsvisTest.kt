package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.IkkePeriodisertFradrag
import org.junit.jupiter.api.Test

internal class BeregningMedFradragBeregnetMånedsvisTest {
    @Test
    fun `summer for enkel beregning`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            fradragStrategy = FradragStrategy.Enslig
        )
        beregning.getSumYtelse() shouldBe 250116
        beregning.getSumFradrag() shouldBe 0
    }

    @Test
    fun `fradrag for alle perioder`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt, månedsbeløp = 1000.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            fradragStrategy = FradragStrategy.Enslig
        )

        beregning.getSumYtelse() shouldBe 238116
        beregning.getSumFradrag() shouldBe 12000
    }

    @Test
    fun `fradrag for enkelte perioder`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 500.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 6000.0,
                    periode = Periode(1.juni(2020), 30.juni(2020)),
                    tilhører = FradragTilhører.BRUKER
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig
        )

        beregning.getSumYtelse() shouldBe 238616
        beregning.getSumFradrag() shouldBe 11500
    }

    @Test
    fun `overlappende fradrag for samme periode`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 500.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Kontantstøtte,
                    månedsbeløp = 6000.0,
                    periode = Periode(1.januar(2020), 31.januar(2020)),
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            fradragStrategy = FradragStrategy.Enslig
        )

        beregning.getSumYtelse() shouldBe 238116
        beregning.getSumFradrag() shouldBe 12000
    }

    /**
     * Månedsbeløp: Jan-Apr = 20637,32 -> * 0.02 = 412,7464
     * Månedsbeløp: Mai-Des = 20945,87 -> * 0.02 = 418,9174
     * Beløpsgrense regnet måned for måned: (412,7464 * 4) + (418,9174 * 8) = 5002,3248
     *
     * Fradrag: 245117 -> 20426,42 pr mnd
     *
     * Utbetalt Jan-Apr: 20637,32 - 20426,42 = 210,9 -> rundes til 211 -- "får" 0,1 pr mnd = 0,4 totalt
     * Utbetalt Mai-Des: 20945,87 - 20426,42 = 519,46 -> rundes til 519 -- "mister" 0,46 pr mnd = 3,68 totalt
     * "Mister" totalt 3,28 kr pga avrunding av månedsbeløp
     * Totalt (tatt høyde for avrunding av månedsbeløp): 4996
     *
     * Dersom vi ikke hadde tatt høyde for avrunding ville vi hatt
     * Jan-Apr: 210,9 pr mnd
     * Mai-Des: 519,46 pr mnd
     * Totalt: 4999.28
     *
     * Total (uten avrunding) - "det vi mister pga avrunding" = 4996
     */
    @Test
    fun `sum under minstebeløp for utbetaling (2 prosent av høy sats)`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 20426.42,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            fradragStrategy = FradragStrategy.Enslig
        )

        beregning.getSumYtelse() shouldBe 4996
        beregning.getSumFradrag() shouldBe 245117.0.plusOrMinus(0.5)
    }

    @Test
    fun `generer bare bare id og opprettet en gang for hvert objekt`() {
        val beregning = BeregningFactory.ny(
            periode = Periode(1.januar(2020), 31.mars(2020)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = Periode(1.januar(2020), 31.mars(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            fradragStrategy = FradragStrategy.Enslig
        )
        beregning.getId() shouldBe beregning.getId()
        beregning.getOpprettet() shouldBe beregning.getOpprettet()
    }

    @Test
    fun `fradrag inkluderes kun i den måneden de er aktuelle`() {
        val periode = Periode(1.januar(2020), 31.mars(2020))

        val totaltFradrag = 100000.0

        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = totaltFradrag,
                    periode = Periode(1.januar(2020), 31.januar(2020)),
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            fradragStrategy = FradragStrategy.Enslig
        )

        beregning.getSumYtelse() shouldBe 41274
        beregning.getSumFradrag() shouldBe 20637.32
        val grouped = beregning.getMånedsberegninger().groupBy { it.getPeriode() }
        val januar = grouped[Periode(1.januar(2020), 31.januar(2020))]!!.first()
        januar.getSumFradrag() shouldBe 20637.32
        januar.getSumYtelse() shouldBe 0
        val februar = grouped[Periode(1.februar(2020), 29.februar(2020))]!!.first()
        februar.getSumFradrag() shouldBe 0
        februar.getSumYtelse() shouldBe 20637
    }

    @Test
    fun `To beregninger med samme totalsum for fradrag, men i forskjellige perioder gir ikke nødvendigvis samme resultat`() {
        val periode = Periode(1.januar(2020), 31.mars(2020))

        val totaltFradrag = 100000.0

        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = totaltFradrag,
                    periode = Periode(1.januar(2020), 31.januar(2020)),
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            fradragStrategy = FradragStrategy.Enslig
        )

        val beregning2 = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = totaltFradrag,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            fradragStrategy = FradragStrategy.Enslig
        )

        beregning.getMånedsberegninger() shouldNotBe beregning2.getMånedsberegninger()
        beregning.getSumFradrag() shouldNotBe beregning2.getSumFradrag()
        beregning.getSumYtelse() shouldNotBe beregning2.getSumYtelse()
    }
}
