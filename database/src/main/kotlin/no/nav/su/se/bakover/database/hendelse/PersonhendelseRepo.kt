package no.nav.su.se.bakover.database.hendelse

import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.Personhendelse

interface PersonhendelseRepo {
    fun lagre(personhendelse: Personhendelse.Ny, saksnummer: Saksnummer)
    // TODO jah: Disse er litt premature. Kommer i neste PR
    // fun hent(hendelseId: String): Personhendelse.Persistert?
    // fun oppdaterOppgave(hendelseId: String, oppgaveId: OppgaveId)
}
