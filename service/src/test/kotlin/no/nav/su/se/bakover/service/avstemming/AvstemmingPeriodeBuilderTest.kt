package no.nav.su.se.bakover.service.avstemming

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset

internal class AvstemmingPeriodeBuilderTest {

    // now() yields 10 januar 2020
    private val fixedClock = Clock.fixed(1.januar(2020).plusDays(9).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `periode for første avstemming`() {
        val periode = AvstemmingPeriodeBuilder(null, fixedClock).build()
        periode.fraOgMed shouldBe 1.januar(2020).startOfDay()
        periode.tilOgMed shouldBe 9.januar(2020).endOfDay()
    }

    @Test
    fun `periode for påfølgende avstemming`() {
        val periode = AvstemmingPeriodeBuilder(
            Avstemming(
                opprettet = now(fixedClock),
                fraOgMed = 1.januar(2020).startOfDay(),
                tilOgMed = 4.januar(2020).endOfDay(),
                utbetalinger = emptyList()
            ),
            fixedClock
        ).build()
        periode.fraOgMed shouldBe 5.januar(2020).startOfDay()
        periode.tilOgMed shouldBe 9.januar(2020).endOfDay()
    }
}
