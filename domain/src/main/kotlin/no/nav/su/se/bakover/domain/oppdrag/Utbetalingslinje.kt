package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import java.time.LocalDate

data class Utbetalingslinje(
    val id: UUID30 = UUID30.randomUUID(), // delytelseId,
    val opprettet: Tidspunkt = Tidspunkt.now(),
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    var forrigeUtbetalingslinjeId: UUID30?,
    val beløp: Int
) {
    init {
        require(fraOgMed < tilOgMed) { "fraOgMed må være tidligere enn tilOgMed" }
    }

    fun link(other: Utbetalingslinje) {
        forrigeUtbetalingslinjeId = other.id
    }
}
