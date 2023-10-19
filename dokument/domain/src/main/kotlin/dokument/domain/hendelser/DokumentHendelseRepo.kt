package dokument.domain.hendelser

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import java.util.UUID

interface DokumentHendelseRepo {
    fun lagre(hendelse: LagretDokumentHendelse, hendelseFil: HendelseFil, sessionContext: SessionContext? = null)
    fun hentForSak(sakId: UUID, sessionContext: SessionContext): List<LagretDokumentHendelse>
}
