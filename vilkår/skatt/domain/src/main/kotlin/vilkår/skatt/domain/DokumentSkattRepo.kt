package vilkår.skatt.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface DokumentSkattRepo {
    fun hent(id: UUID): Skattedokument?
    fun lagre(dok: Skattedokument)
    fun lagre(dok: Skattedokument, session: SessionContext)
    fun hentDokumenterForJournalføring(): List<Skattedokument.Generert>
}
