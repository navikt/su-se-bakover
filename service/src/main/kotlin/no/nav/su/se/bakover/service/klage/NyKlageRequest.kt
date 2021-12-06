package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class NyKlageRequest(
    val sakId: UUID,
    private val saksbehandler: NavIdentBruker.Saksbehandler,
    val journalpostId: JournalpostId,
    private val datoKlageMottatt: LocalDate,
) {
    fun toKlage(
        saksnummer: Saksnummer,
        fnr: Fnr,
        oppgaveId: OppgaveId,
        clock: Clock,
    ): OpprettetKlage {
        return Klage.ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            datoKlageMottatt = datoKlageMottatt,
            clock = clock,
        )
    }
}
