package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID

data class OpprettetKlage private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val journalpostId: JournalpostId,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
) : Klage() {

    companion object {
        fun create(
            id: UUID,
            opprettet: Tidspunkt,
            sakId: UUID,
            journalpostId: JournalpostId,
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): OpprettetKlage = OpprettetKlage(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            journalpostId = journalpostId,
            saksbehandler = saksbehandler,
        )
    }
}
