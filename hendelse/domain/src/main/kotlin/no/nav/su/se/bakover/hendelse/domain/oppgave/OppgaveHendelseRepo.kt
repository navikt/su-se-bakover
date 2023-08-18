package no.nav.su.se.bakover.hendelse.domain.oppgave

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

interface OppgaveHendelseRepo {
    fun lagre(hendelse: OppgaveHendelse, sessionContext: SessionContext)
    fun hentSisteVersjonFor(sakId: UUID): Hendelsesversjon?
    fun hentForSak(sakId: UUID): List<OppgaveHendelse>
}
