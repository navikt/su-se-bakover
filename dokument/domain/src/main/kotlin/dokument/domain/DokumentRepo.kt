package dokument.domain

import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface DokumentRepo {
    /** Krever transactionContext siden vi gjør 2 inserts. */
    fun lagre(dokument: Dokument.MedMetadata, transactionContext: TransactionContext? = null)
    fun hentDokument(id: UUID): Dokument.MedMetadata?
    fun hentForSak(id: UUID): List<Dokument.MedMetadata>
    fun hentForSøknad(id: UUID): List<Dokument.MedMetadata>
    fun hentForVedtak(id: UUID): List<Dokument.MedMetadata>
    fun hentForRevurdering(id: UUID): List<Dokument.MedMetadata>
    fun hentForKlage(id: UUID): List<Dokument.MedMetadata>

    fun hentDokumentdistribusjon(id: UUID): Dokumentdistribusjon?
    fun hentDokumenterForJournalføring(antallSomSkalHentes: Int = 10): List<Dokumentdistribusjon>
    fun hentDokumenterForDistribusjon(antallSomSkalHentes: Int = 10): List<Dokumentdistribusjon>
    fun oppdaterDokumentdistribusjon(dokumentdistribusjon: Dokumentdistribusjon)

    fun defaultTransactionContext(): TransactionContext
}
