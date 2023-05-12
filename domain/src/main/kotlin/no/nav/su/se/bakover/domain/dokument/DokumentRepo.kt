package no.nav.su.se.bakover.domain.dokument

import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import java.util.UUID

interface DokumentRepo {
    fun lagre(dokument: Dokument.MedMetadata, transactionContext: TransactionContext = defaultTransactionContext())
    fun hentDokument(id: UUID): Dokument.MedMetadata?
    fun hentForSak(id: UUID): List<Dokument.MedMetadata>
    fun hentForSøknad(id: UUID): List<Dokument.MedMetadata>
    fun hentForVedtak(id: UUID): List<Dokument.MedMetadata>
    fun hentForRevurdering(id: UUID): List<Dokument.MedMetadata>
    fun hentForKlage(id: UUID): List<Dokument.MedMetadata>

    fun hentDokumentdistribusjon(id: UUID): Dokumentdistribusjon?
    fun hentDokumenterForDistribusjon(): List<Dokumentdistribusjon>
    fun oppdaterDokumentdistribusjon(dokumentdistribusjon: Dokumentdistribusjon)

    fun lagreSkatteDokument(dokument: Skattedokument, tc: TransactionContext)

    fun defaultTransactionContext(): TransactionContext
}
