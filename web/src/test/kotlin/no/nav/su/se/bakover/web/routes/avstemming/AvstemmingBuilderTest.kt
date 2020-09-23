package no.nav.su.se.bakover.web.routes.avstemming

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset

internal class AvstemmingBuilderTest {

    // now() yields 10 januar 2020
    private val fixedClock =
        Clock.fixed(1.januar(2020).plusDays(9).startOfDay(), ZoneOffset.UTC)

    @Test
    fun `periode for første avstemming`() {
        val periode = AvstemmingBuilder.AvstemmingPeriodeBuilder(null, fixedClock).build()
        periode.fom shouldBe 1.januar(2020).startOfDay()
        periode.tom shouldBe 9.januar(2020).endOfDay()
    }

    @Test
    fun `periode for påfølgende avstemming`() {
        val periode = AvstemmingBuilder.AvstemmingPeriodeBuilder(
            Avstemming(
                opprettet = now(fixedClock),
                fom = 1.januar(2020).startOfDay(),
                tom = 4.januar(2020).endOfDay(),
                utbetalinger = emptyList()
            ),
            fixedClock
        ).build()
        periode.fom shouldBe 5.januar(2020).startOfDay()
        periode.tom shouldBe 9.januar(2020).endOfDay()
    }
}
