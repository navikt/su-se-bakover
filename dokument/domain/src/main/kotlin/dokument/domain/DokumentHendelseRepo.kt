package dokument.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface DokumentHendelseRepo {
    fun lagre(hendelse: LagretDokumentHendelse, sessionContext: SessionContext)
    fun hentForSak(sakId: UUID, sessionContext: SessionContext): List<LagretDokumentHendelse>
}
