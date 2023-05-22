package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface DokumentSkattRepo {
    fun hent(id: UUID): Skattedokument?
    fun lagre(dok: Skattedokument)
    fun lagre(dok: Skattedokument, txc: SessionContext)
    fun hentDokumenterForJournalføring(): List<Skattedokument.Generert>
}
