package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import java.time.Clock
import java.util.UUID

data class NyKlageRequest(
    val sakId: UUID,
    private val navIdent: String,
    private val journalpostId: String,
) {
    fun toKlage(clock: Clock): OpprettetKlage {
        return Klage.ny(
            sakId = sakId,
            journalpostId = JournalpostId(value = journalpostId),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = navIdent),
            clock = clock,
        )
    }
}
