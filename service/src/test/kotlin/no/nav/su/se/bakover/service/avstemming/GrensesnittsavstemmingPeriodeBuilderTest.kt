package no.nav.su.se.bakover.service.avstemming

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Fagområde
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.time.Clock

internal class GrensesnittsavstemmingPeriodeBuilderTest {

    // 10 januar 2021
    private val fixedClock = Clock.fixed(1.januar(2021).plusDays(9).startOfDay().instant, zoneIdOslo)

    @Test
    fun `periode for første avstemming`() {
        val periode = GrensesnittsavstemmingPeriodeBuilder(null, fixedClock).build()
        periode.fraOgMed shouldBe 1.januar(2021).startOfDay()
        periode.tilOgMed shouldBe 9.januar(2021).endOfDay()
    }

    @Test
    fun `periode for påfølgende avstemming`() {
        val periode = GrensesnittsavstemmingPeriodeBuilder(
            Avstemming.Grensesnittavstemming(
                opprettet = fixedTidspunkt,
                fraOgMed = 1.januar(2021).startOfDay(),
                tilOgMed = 4.januar(2021).endOfDay(),
                utbetalinger = emptyList(),
                fagområde = Fagområde.SUUFORE,
            ),
            fixedClock,
        ).build()
        periode.fraOgMed shouldBe 5.januar(2021).startOfDay()
        periode.tilOgMed shouldBe 9.januar(2021).endOfDay()
    }
}
