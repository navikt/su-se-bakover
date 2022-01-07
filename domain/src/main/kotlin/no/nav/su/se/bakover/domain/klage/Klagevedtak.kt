package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

/**
 * Representerer ett fattet klagevedtak av Kabal.
 */
data class UprosessertKlagevedtak(
    val id: UUID,
    val eventId: String,
    val klageId: UUID,
    val utfall: KlagevedtakUtfall,
    val vedtaksbrevReferanse: String,
) {
    fun tilProsessert(oppgaveId: OppgaveId?) = ProsessertKlagevedtak(
        id = id,
        eventId = eventId,
        klageId = klageId,
        utfall = utfall,
        vedtaksbrevReferanse = vedtaksbrevReferanse,
        oppgaveId = oppgaveId,
    )
}

data class ProsessertKlagevedtak(
    val id: UUID,
    val eventId: String,
    val klageId: UUID,
    val utfall: KlagevedtakUtfall,
    val vedtaksbrevReferanse: String,
    val oppgaveId: OppgaveId?,
)

enum class KlagevedtakUtfall {
    TRUKKET,
    RETUR,
    OPPHEVET,
    MEDHOLD,
    DELVIS_MEDHOLD,
    STADFESTELSE,
    UGUNST,
    AVVIST
}

sealed interface KanIkkeTolkeKlagevedtak {
    object KunneIkkeDeserialisere : KanIkkeTolkeKlagevedtak
    object UgyldigeVerdier : KanIkkeTolkeKlagevedtak
}
