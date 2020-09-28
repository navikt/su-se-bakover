package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import java.time.LocalDate

data class Utbetalingslinje(
    val id: UUID30 = UUID30.randomUUID(), // delytelseId,
    val opprettet: Tidspunkt = now(),
    val fom: LocalDate,
    val tom: LocalDate,
    var forrigeUtbetalingslinjeId: UUID30?,
    val beløp: Int

) {
    fun link(other: Utbetalingslinje) {
        forrigeUtbetalingslinjeId = other.id
    }
}
