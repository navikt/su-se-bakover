package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.now
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Oppdragslinje(
    val id: UUID = UUID.randomUUID(), // delytelseId,
    val opprettet: Instant = now(),
    val fom: LocalDate,
    val tom: LocalDate,
    var forrigeOppdragslinjeId: UUID?,
    val beløp: Int

) {
    fun link(other: Oppdragslinje) {
        forrigeOppdragslinjeId = other.id
    }
}
