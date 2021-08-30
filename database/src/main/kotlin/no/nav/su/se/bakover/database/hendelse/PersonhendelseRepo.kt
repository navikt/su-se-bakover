package no.nav.su.se.bakover.database.hendelse

import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

interface PersonhendelseRepo {
    fun lagre(personhendelse: Personhendelse.Ny, id: UUID, sakId: UUID)
    fun hentPersonhendelserUtenOppgave(): List<Personhendelse.TilknyttetSak>
    fun oppdaterOppgave(id: UUID, oppgaveId: OppgaveId)
}
