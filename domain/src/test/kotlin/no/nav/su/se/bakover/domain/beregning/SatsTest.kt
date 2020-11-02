package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test

internal class SatsTest {
    @Test
    fun `periodiserer sats og bruker to desimalers presisjon`() {
        val p1 = Sats.HØY.periodiser(Periode(1.januar(2020), 31.januar(2020)))
        p1 shouldBe mapOf(Periode(1.januar(2020), 31.januar(2020)) to 20637.32)

        val p2 = Sats.HØY.periodiser(Periode(1.april(2020), 30.juni(2020)))
        p2 shouldBe mapOf(
            Periode(1.april(2020), 30.april(2020)) to 20637.32,
            Periode(1.mai(2020), 31.mai(2020)) to 20945.87,
            Periode(1.juni(2020), 30.juni(2020)) to 20945.87,
        )
    }

    @Test
    fun `håndterer avrunding av beløp på en akseptabel måte`() {
        Sats.HØY.årsbeløp(1.januar(2020)) shouldBe 247648
        Sats.HØY.årsbeløp(31.desember(2020)) shouldBe 251350

        val p1 = Sats.HØY.periodiser(Periode(1.januar(2020), 30.april(2020)))
        p1[Periode(1.januar(2020), 31.januar(2020))] shouldBe 20637.32
        val sumOfJanApr = p1.values.sum()
        sumOfJanApr shouldBe 82549.28

        val p2 = Sats.HØY.periodiser(Periode(1.mai(2020), 31.desember(2020)))
        p2[Periode(1.mai(2020), 31.mai(2020))] shouldBe 20945.87
        val sumOfMaiDes = p2.values.sum()
        sumOfMaiDes shouldBe 167566.96
    }

    @Test
    fun `beløp for år og måneder`() {
        Sats.HØY.årsbeløp(1.januar(2020)) shouldBe 247648
        Sats.HØY.årsbeløp(31.desember(2020)) shouldBe 251350
        Sats.HØY.månedsbeløp(1.januar(2020)) shouldBe 20637.32
        Sats.HØY.månedsbeløp(31.desember(2020)) shouldBe 20945.87
    }

    @Test
    fun `kalkulerer to prosent av høy sats for perioder`() {
        Sats.HØY.toProsentAvHøySats(Periode(1.januar(2020), 31.mars(2020))) shouldBe 1238
        Sats.HØY.toProsentAvHøySats(Periode(1.januar(2020), 31.desember(2020))) shouldBe 5002
        Sats.ORDINÆR.toProsentAvHøySats(Periode(1.januar(2020), 31.mars(2020))) shouldBe 1238
        Sats.ORDINÆR.toProsentAvHøySats(Periode(1.januar(2020), 31.desember(2020))) shouldBe 5002
    }
}
