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
    val endringskode: Endringskode = Endringskode.NY,
    var refOppdragslinjeId: UUID?,
    val refSakId: UUID,
    val beløp: Int,
    val klassekode: Klassekode = Klassekode.KLASSE,
    var status: Status? = null,
    var statusFom: LocalDate? = null,
    val beregningsfrekvens: Beregningsfrekvens = Beregningsfrekvens.MND,
    val saksbehandler: String,
    val attestant: String? = null

) {
    fun link(other: Oppdragslinje) {
        refOppdragslinjeId = other.id
    }
    enum class Endringskode {
        NY
    }

    enum class Beregningsfrekvens {
        MND
    }

    enum class Status {
        OPPH,
        HVIL,
        SPER,
        REAK
    }

    enum class Klassekode {
        KLASSE // TODO decide with OS
    }
}
