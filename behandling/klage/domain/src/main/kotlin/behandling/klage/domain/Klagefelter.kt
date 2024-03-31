package behandling.klage.domain

import behandling.domain.Behandling
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

interface Klagefelter : Behandling {
    override val id: KlageId
    override val opprettet: Tidspunkt
    override val sakId: UUID
    override val saksnummer: Saksnummer
    override val fnr: Fnr
    val journalpostId: JournalpostId
    val oppgaveId: OppgaveId
    val datoKlageMottatt: LocalDate
    val saksbehandler: NavIdentBruker.Saksbehandler
}
