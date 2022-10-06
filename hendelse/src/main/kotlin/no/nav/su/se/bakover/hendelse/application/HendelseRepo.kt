package no.nav.su.se.bakover.hendelse.application

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface HendelseRepo {
    fun persisterHendelse(hendelse: Hendelse, sessionContext: SessionContext)
    fun hentHendelserForSak(sakId: UUID): List<Hendelse>
}
