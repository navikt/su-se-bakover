package no.nav.su.se.bakover.domain.oppdrag

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Oppdragslinje(
    val id: UUID, // delytelseId,
    val opprettet: Instant = Instant.now(),
    val fom: LocalDate,
    val tom: LocalDate,
    val endringskode: Endringskode
) {
    enum class Endringskode {
        NY, ENDR
    }
}
