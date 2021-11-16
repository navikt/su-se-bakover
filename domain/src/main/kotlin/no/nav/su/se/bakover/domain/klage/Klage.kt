package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.time.Clock
import java.util.UUID

data class Klage(
    val id: UUID,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val journalpostId: JournalpostId,
    val saksbehandler: NavIdentBruker.Saksbehandler
) {
    companion object {
        fun ny(
            sakId: UUID,
            journalpostId: JournalpostId,
            saksbehandler: NavIdentBruker.Saksbehandler,
            clock: Clock = Clock.systemUTC()
        ) = Klage(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            sakId = sakId,
            journalpostId = journalpostId,
            saksbehandler = saksbehandler,
        )
    }
}
