package no.nav.su.se.bakover.database.hendelse

import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.PdlHendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId

interface HendelseRepo {
    fun lagre(pdlHendelse: PdlHendelse.Ny, saksnummer: Saksnummer)
    fun hent(hendelseId: String): PdlHendelse.Persistert?
    fun oppdaterOppgave(hendelseId: String, oppgaveId: OppgaveId)
}
