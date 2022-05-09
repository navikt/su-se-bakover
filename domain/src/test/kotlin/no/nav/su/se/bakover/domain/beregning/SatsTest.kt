package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import org.junit.jupiter.api.Test

internal class SatsTest {
    @Test
    fun `periodiserer sats`() {
        val p1 = Sats.HØY.periodiser(januar(2020))
        p1 shouldBe mapOf(januar(2020) to 20637.32)

        val p2 = Sats.HØY.periodiser(Periode.create(1.april(2020), 30.juni(2020)))
        p2 shouldBe mapOf(
            april(2020) to Sats.HØY.månedsbeløp(1.april(2020)),
            mai(2020) to Sats.HØY.månedsbeløp(1.mai(2020)),
            juni(2020) to Sats.HØY.månedsbeløp(1.mai(2020)),
        )
    }

    @Test
    fun `håndterer avrunding av beløp på en akseptabel måte`() {
        Sats.HØY.årsbeløp(1.januar(2020)) shouldBe 247648.0.plusOrMinus(0.5)
        Sats.HØY.årsbeløp(31.desember(2020)) shouldBe 251350.0.plusOrMinus(0.5)

        val p1 = Sats.HØY.periodiser(Periode.create(1.januar(2020), 30.april(2020)))
        p1[januar(2020)] shouldBe 20637.32.plusOrMinus(0.5)
        val sumOfJanApr = p1.values.sum()
        sumOfJanApr shouldBe 82549.28.plusOrMinus(0.5)

        val p2 = Sats.HØY.periodiser(Periode.create(1.mai(2020), 31.desember(2020)))
        p2[mai(2020)] shouldBe 20945.87.plusOrMinus(0.5)
        val sumOfMaiDes = p2.values.sum()
        sumOfMaiDes shouldBe 167566.96.plusOrMinus(0.5)
    }

    @Test
    fun `beløp for år og måneder`() {
        Sats.HØY.årsbeløp(1.januar(2020)) shouldBe 247648.0.plusOrMinus(0.5)
        Sats.HØY.årsbeløp(31.desember(2020)) shouldBe 251350.0.plusOrMinus(0.5)
        Sats.HØY.månedsbeløp(1.januar(2020)) shouldBe 20637.32.plusOrMinus(0.5)
        Sats.HØY.månedsbeløp(31.desember(2020)) shouldBe 20945.87.plusOrMinus(0.5)
    }

    @Test
    fun `kalkulerer to prosent av høy sats for perioder`() {
        Sats.toProsentAvHøy(Periode.create(1.januar(2020), 31.mars(2020))) shouldBe 1238.0.plusOrMinus(0.5)
        Sats.toProsentAvHøy(år(2020)) shouldBe 5002.0.plusOrMinus(0.5)
    }
}
