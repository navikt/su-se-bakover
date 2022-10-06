package no.nav.su.se.bakover.domain.jobcontext

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset

internal class SendPåminnelseNyStønadsperiodeContextTest {
    @Test
    fun `id er basert på jobbnavn og måned`() {
        val førsteJanuar = fixedClock(1.januar(2021))
        val fjortendeJanuar = fixedClock(14.januar(2021))
        val trettiførsteJanuar = fixedClock(31.januar(2021))

        SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(førsteJanuar) shouldBe SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
            fjortendeJanuar,
        ).also {
            it.name shouldBe "SendPåminnelseNyStønadsperiode"
            it.yearMonth shouldBe YearMonth.of(2021, Month.JANUARY)
        }
        SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(førsteJanuar) shouldBe SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
            trettiførsteJanuar,
        )
        SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(fjortendeJanuar) shouldBe SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
            trettiførsteJanuar,
        )

        val femteFebruar = fixedClock(5.februar(2021))

        SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(førsteJanuar) shouldNotBe SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
            femteFebruar,
        ).also {
            it.name shouldBe "SendPåminnelseNyStønadsperiode"
            it.yearMonth shouldBe YearMonth.of(2021, Month.FEBRUARY)
        }
    }

    private fun fixedClock(dato: LocalDate): Clock {
        return Clock.fixed(dato.atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }
}
