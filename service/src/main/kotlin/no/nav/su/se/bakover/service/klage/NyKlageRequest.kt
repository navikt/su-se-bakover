package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class NyKlageRequest(
    val sakId: UUID,
    private val saksbehandler: NavIdentBruker.Saksbehandler,
    private val journalpostId: JournalpostId,
    private val datoKlageMottatt: LocalDate,
) {
    fun toKlage(clock: Clock): OpprettetKlage {
        return Klage.ny(
            sakId = sakId,
            journalpostId = journalpostId,
            saksbehandler = saksbehandler,
            datoKlageMottatt = datoKlageMottatt,
            clock = clock,
        )
    }
}
