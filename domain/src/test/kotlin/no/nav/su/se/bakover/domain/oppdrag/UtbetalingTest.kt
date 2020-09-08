package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.collections.shouldContainInOrder
import no.nav.su.se.bakover.common.UUID30
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.util.UUID

internal class UtbetalingTest {
    @Test
    fun `sorts utbetalinger ascending by opprettet`() {
        val first = Utbetaling(
            opprettet = LocalDate.of(2020, Month.APRIL, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            oppdragId = UUID30.randomUUID(),
            behandlingId = UUID.randomUUID(),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList()
        )
        val second = Utbetaling(
            opprettet = LocalDate.of(2020, Month.JANUARY, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            oppdragId = UUID30.randomUUID(),
            behandlingId = UUID.randomUUID(),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList()
        )
        val third = Utbetaling(
            opprettet = LocalDate.of(2020, Month.JULY, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            oppdragId = UUID30.randomUUID(),
            behandlingId = UUID.randomUUID(),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList()
        )
        val sorted = listOf(first, second, third).sortedWith(Utbetaling.Opprettet)
        sorted shouldContainInOrder listOf(second, first, third)
    }
}
