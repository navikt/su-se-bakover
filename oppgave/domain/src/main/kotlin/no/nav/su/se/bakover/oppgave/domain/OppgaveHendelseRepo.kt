package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface OppgaveHendelseRepo {
    fun lagre(hendelse: OppgaveHendelse, sessionContext: SessionContext)
    fun hentForSak(sakId: UUID): List<OppgaveHendelse>
}
