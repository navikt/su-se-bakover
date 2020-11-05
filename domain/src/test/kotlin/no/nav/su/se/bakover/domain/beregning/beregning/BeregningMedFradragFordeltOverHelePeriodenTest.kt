package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.PeriodeFradrag
import org.junit.jupiter.api.Test

internal class BeregningMedFradragFordeltOverHelePeriodenTest {
    @Test
    fun `summer for enkel beregning`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = emptyList()
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
                PeriodeFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 12000.0,
                    periode = periode
                )
            )
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
                PeriodeFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 6000.0,
                    periode = Periode(1.januar(2020), 31.januar(2020))
                ),
                PeriodeFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 6000.0,
                    periode = Periode(1.juni(2020), 30.juni(2020))
                ),
            )
        )

        beregning.getSumYtelse() shouldBe 238116
        beregning.getSumFradrag() shouldBe 12000
    }

    @Test
    fun `overlappende fradrag for samme periode`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                PeriodeFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 6000.0,
                    periode = Periode(1.januar(2020), 31.januar(2020))
                ),
                PeriodeFradrag(
                    type = Fradragstype.Kontantstøtte,
                    beløp = 6000.0,
                    periode = Periode(1.januar(2020), 31.januar(2020))
                )
            )
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
    fun `sum under minstebeløp for utbetaling (2% av høy sats)`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                PeriodeFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 245117.0,
                    periode = periode
                )
            )
        )

        beregning.getSumYtelse() shouldBe 4996
        beregning.getSumFradrag() shouldBe 245117.0.plusOrMinus(0.5)
        beregning.getSumYtelseErUnderMinstebeløp() shouldBe true
    }

    /**
     * Justerer beløpsgrensen i forhold til antall måneder som beregnes.
     * Månedsbeløp: Jan-Mar = 20637.32 -> * 0.02 = 412,7464
     * Beløpsgrense regnet måned for måned: (412,7464 * 3) = 1238,2392 -> rund til nærmeste hele = 1238
     */
    @Test
    fun `sum under minstebeløp for utbetaling (2% av høy sats) for færre enn 12 måneder`() {
        val periode = Periode(1.januar(2020), 31.mars(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                PeriodeFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 60700.0,
                    periode = periode
                )
            )
        )

        beregning.getSumYtelse() shouldBe 1212
        beregning.getSumFradrag() shouldBe 60700
        (beregning.getSumYtelse() + beregning.getSumFradrag()) shouldBe 61912
        beregning.getSumYtelseErUnderMinstebeløp() shouldBe true
    }

    @Test
    fun `generer bare bare id og opprettet en gang for hvert objekt`() {
        val beregning = BeregningFactory.ny(
            periode = Periode(1.januar(2020), 31.mars(2020)),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
        beregning.id() shouldBe beregning.id()
        beregning.opprettet() shouldBe beregning.opprettet()
    }

    @Test
    fun `alle fradrag blir fordelt over hele perioden`() {
        val periode = Periode(1.januar(2020), 31.mars(2020))

        val totaltFradrag = 100000.0

        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                PeriodeFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = totaltFradrag,
                    periode = Periode(1.januar(2020), 31.januar(2020))
                )
            )
        )

        val forventetTotaltFradrag = Sats.HØY.månedsbeløp(periode.fraOgMed()) * 3

        beregning.getSumYtelse() shouldBe 0
        beregning.getSumFradrag() shouldBe forventetTotaltFradrag
        beregning.getMånedsberegninger().forEach {
            it.getSumFradrag() shouldBe (forventetTotaltFradrag / 3).plusOrMinus(0.5)
            it.getSumYtelse() shouldBe 0
        }
    }

    @Test
    fun `To beregninger med samme totalsum for fradrag, men for forskjellige perioder skal fortsatt gi samme sluttverdier`() {
        val periode = Periode(1.januar(2020), 31.mars(2020))

        val totaltFradrag = 100000.0

        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                PeriodeFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = totaltFradrag,
                    periode = Periode(1.januar(2020), 31.januar(2020))
                )
            )
        )

        val beregning2 = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                PeriodeFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = totaltFradrag,
                    periode = periode
                )
            )
        )

        beregning.getMånedsberegninger() shouldBe beregning2.getMånedsberegninger()
        beregning.getSumFradrag() shouldBe beregning2.getSumFradrag()
        beregning.getSumYtelse() shouldBe beregning2.getSumYtelse()
    }
}
