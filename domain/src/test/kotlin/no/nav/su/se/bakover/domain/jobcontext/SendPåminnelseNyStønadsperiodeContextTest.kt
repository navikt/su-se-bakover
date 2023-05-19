package no.nav.su.se.bakover.domain.jobcontext

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.test.fixedClockAt
import org.junit.jupiter.api.Test
import java.time.Month
import java.time.YearMonth

internal class SendPåminnelseNyStønadsperiodeContextTest {

    @Test
    fun `id er basert på jobbnavn og måned`() {
        val førsteJanuar = fixedClockAt(1.januar(2021))
        val fjortendeJanuar = fixedClockAt(14.januar(2021))
        val trettiførsteJanuar = fixedClockAt(31.januar(2021))

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

        val femteFebruar = fixedClockAt(5.februar(2021))

        SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(førsteJanuar) shouldNotBe SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
            femteFebruar,
        ).also {
            it.name shouldBe "SendPåminnelseNyStønadsperiode"
            it.yearMonth shouldBe YearMonth.of(2021, Month.FEBRUARY)
        }
    }
}
