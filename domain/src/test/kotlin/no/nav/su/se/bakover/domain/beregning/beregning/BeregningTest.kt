package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class BeregningTest {
    @Test
    fun `summer for enkel beregning`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = emptyList()
        )
        beregning.totalSum() shouldBe 250116
        beregning.totaltFradrag() shouldBe 0
        beregning.sum(periode) shouldBe 250116
        beregning.fradrag(periode) shouldBe 0

        beregning.sum(Periode(1.januar(2020), 31.januar(2020))) shouldBe 20637
        beregning.sum(Periode(1.desember(2020), 31.desember(2020))) shouldBe 20946
        beregning.sum(Periode(1.januar(2020), 30.april(2020))) shouldBe 82549
        beregning.fradrag(Periode(1.januar(2020), 30.april(2020))) shouldBe 0
    }

    @Test
    fun `fradrag for alle perioder`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                Fradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 12000.0,
                    periode = periode
                )
            )
        )

        beregning.totalSum() shouldBe 238116
        beregning.totaltFradrag() shouldBe 12000

        beregning.sum(Periode(1.januar(2020), 31.januar(2020))) shouldBe 19637
        beregning.fradrag(Periode(1.januar(2020), 31.januar(2020))) shouldBe 1000
    }

    @Test
    fun `fradrag for enkelte perioder`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                Fradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 6000.0,
                    periode = Periode(1.januar(2020), 31.januar(2020))
                ),
                Fradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 6000.0,
                    periode = Periode(1.juni(2020), 30.juni(2020))
                ),
            )
        )

        beregning.totalSum() shouldBe 238116
        beregning.totaltFradrag() shouldBe 12000

        beregning.sum(Periode(1.januar(2020), 31.januar(2020))) shouldBe 14637
        beregning.fradrag(Periode(1.januar(2020), 31.januar(2020))) shouldBe 6000

        beregning.sum(Periode(1.juni(2020), 30.juni(2020))) shouldBe 14946
        beregning.fradrag(Periode(1.juni(2020), 30.juni(2020))) shouldBe 6000

        beregning.sum(Periode(1.desember(2020), 31.desember(2020))) shouldBe 20946
        beregning.fradrag(Periode(1.desember(2020), 31.desember(2020))) shouldBe 0
    }

    @Test
    fun `overlappende fradrag for samme periode`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                Fradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 6000.0,
                    periode = Periode(1.januar(2020), 31.januar(2020))
                ),
                Fradrag(
                    type = Fradragstype.Kontantstøtte,
                    beløp = 6000.0,
                    periode = Periode(1.januar(2020), 31.januar(2020))
                )
            )
        )

        beregning.totalSum() shouldBe 238116
        beregning.totaltFradrag() shouldBe 12000

        beregning.sum(Periode(1.januar(2020), 31.januar(2020))) shouldBe 8637
        beregning.fradrag(Periode(1.januar(2020), 31.januar(2020))) shouldBe 12000

        beregning.sum(Periode(1.desember(2020), 31.desember(2020))) shouldBe 20946
        beregning.fradrag(Periode(1.desember(2020), 31.desember(2020))) shouldBe 0
    }

    /**
     * Månedsbeløp: Jan-Apr = 20637.32 -> * 0.02 = 412,7464
     * Månedsbeløp: Mai-Des = 20945.87 -> * 0.02 = 418,9174
     * Beløpsgrense regnet måned for måned: (412,7464 * 4) + (418,9174) * 8 = 5002,3248 -> rund til nærmeste hele = 5002
     * Beløpsgrense regnet fra årsbeløp med høy sats: 250116 -> * 0.02 -> 5002,32
     */
    @Test
    fun `sum lik minstebeløp for utbetaling (2% av høy sats)`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                Fradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 245114.0,
                    periode = periode
                )
            )
        )

        beregning.totalSum() shouldBe 5002
        beregning.totaltFradrag() shouldBe 245114
        (beregning.totalSum() + beregning.totaltFradrag()) shouldBe 250116
        beregning.sumUnderMinstegrense() shouldBe false
    }

    /**
     * Månedsbeløp: Jan-Apr = 20637.32 -> * 0.02 = 412,7464
     * Månedsbeløp: Mai-Des = 20945.87 -> * 0.02 = 418,9174
     * Beløpsgrense regnet måned for måned: (412,7464 * 4) + (418,9174) * 8 = 5002,3248 -> rund til nærmeste hele = 5002
     * Beløpsgrense regnet fra årsbeløp med høy sats: 250116 -> * 0.02 -> 5002,32
     */
    @Test
    fun `sum under minstebeløp for utbetaling (2% av høy sats)`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                Fradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 245115.0,
                    periode = periode
                )
            )
        )

        beregning.totalSum() shouldBe 5001
        beregning.totaltFradrag() shouldBe 245115
        (beregning.totalSum() + beregning.totaltFradrag()) shouldBe 250116
        beregning.sumUnderMinstegrense() shouldBe true
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
                Fradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 60700.0,
                    periode = periode
                )
            )
        )

        beregning.totalSum() shouldBe 1212
        beregning.totaltFradrag() shouldBe 60700
        (beregning.totalSum() + beregning.totaltFradrag()) shouldBe 61912
        beregning.sumUnderMinstegrense() shouldBe true
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
}
