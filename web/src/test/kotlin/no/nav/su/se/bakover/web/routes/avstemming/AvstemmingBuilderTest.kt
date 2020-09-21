package no.nav.su.se.bakover.web.routes.avstemming

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class AvstemmingBuilderTest {

    // now() yields 10 januar 2020
    private val fixedClock = Clock.fixed(1.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC).plus(9, ChronoUnit.DAYS), ZoneOffset.UTC)

    @Test
    fun `periode for første avstemming`() {
        val periode = AvstemmingBuilder.AvstemmingPeriodeBuilder(null, fixedClock).build()
        periode.fom shouldBe 1.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC)
        periode.tom shouldBe 8.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC)
    }

    @Test
    fun `periode for påfølgende avstemming`() {
        val periode = AvstemmingBuilder.AvstemmingPeriodeBuilder(
            Avstemming(
                opprettet = now(fixedClock),
                fom = 1.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                tom = 4.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                utbetalinger = emptyList()
            ),
            fixedClock
        ).build()
        periode.fom shouldBe 4.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC)
        periode.tom shouldBe 8.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC)
    }
}
