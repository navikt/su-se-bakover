package no.nav.su.se.bakover.database.hendelse

import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import java.util.UUID

interface PersonhendelseRepo {
    fun lagre(personhendelse: Personhendelse.Ny, id: UUID, sakId: UUID)
    // TODO jah: Disse er litt premature. Kommer i neste PR
    // fun hent(hendelseId: String): Personhendelse.Persistert?
    // fun oppdaterOppgave(hendelseId: String, oppgaveId: OppgaveId)
}
