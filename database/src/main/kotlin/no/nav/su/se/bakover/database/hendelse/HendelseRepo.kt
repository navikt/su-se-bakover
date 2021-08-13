package no.nav.su.se.bakover.database.hendelse

import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId

interface HendelseRepo {
    fun lagre(personhendelse: Personhendelse.Ny, saksnummer: Saksnummer)
    fun hent(hendelseId: String): Personhendelse.Persistert?
    fun oppdaterOppgave(hendelseId: String, oppgaveId: OppgaveId)
}
