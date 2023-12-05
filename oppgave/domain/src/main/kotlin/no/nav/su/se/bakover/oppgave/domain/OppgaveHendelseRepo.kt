package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import java.util.UUID

interface OppgaveHendelseRepo {
    fun lagre(hendelse: OppgaveHendelse, sessionContext: SessionContext)
    fun hentForSak(sakId: UUID, sessionContext: SessionContext? = null): List<OppgaveHendelse>
    fun hentHendelseForRelatert(relatertHendelseId: HendelseId, sakId: UUID, sessionContext: SessionContext? = null): OppgaveHendelse?
}
