package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

/**
 * Representerer ett fattet klagevedtak av Kabal.
 */
sealed class Klagevedtak {
    abstract val id: UUID
    abstract val eventId: String
    abstract val klageId: UUID
    abstract val utfall: Utfall
    abstract val vedtaksbrevReferanse: String

    data class Uprosessert(
        override val id: UUID,
        override val eventId: String,
        override val klageId: UUID,
        override val utfall: Utfall,
        override val vedtaksbrevReferanse: String
    ) : Klagevedtak() {
        fun tilProsessert(oppgaveId: OppgaveId?) = Prosessert(
            id = id,
            eventId = eventId,
            klageId = klageId,
            utfall = utfall,
            vedtaksbrevReferanse = vedtaksbrevReferanse,
            oppgaveId = oppgaveId,
        )
    }

    data class Prosessert(
        override val id: UUID,
        override val eventId: String,
        override val klageId: UUID,
        override val utfall: Utfall,
        override val vedtaksbrevReferanse: String,
        val oppgaveId: OppgaveId?,
    ) : Klagevedtak()

    enum class Utfall {
        TRUKKET,
        RETUR,
        OPPHEVET,
        MEDHOLD,
        DELVIS_MEDHOLD,
        STADFESTELSE,
        UGUNST,
        AVVIST
    }
}
