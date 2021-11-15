package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID

data class Klage(
    val id: UUID,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val journalpostId: JournalpostId,
    val saksbehandler: NavIdentBruker.Saksbehandler
)
