package no.nav.su.se.bakover.database.hendelse

import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import java.util.UUID

interface PersonhendelseRepo {
    fun lagre(personhendelse: Personhendelse.Ny, id: UUID, sakId: UUID)
    fun hentPersonhendelserUtenOppgave(): List<Personhendelse.TilknyttetSak>

    // TODO jah: Knytt inn i lagre
    // fun oppdaterOppgave(hendelseId: String, oppgaveId: OppgaveId)
}
