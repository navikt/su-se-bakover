package dokument.domain

import arrow.core.Either
import dokument.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * Representerer tilstanden i prosessen for journalføringen og distribusjonen av et [Dokument].
 */
data class Dokumentdistribusjon(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt,
    val endret: Tidspunkt = opprettet,
    val dokument: Dokument.MedMetadata,
    val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
) {
    fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, Dokumentdistribusjon> {
        return journalføringOgBrevdistribusjon.journalfør(journalfør)
            .map { copy(journalføringOgBrevdistribusjon = it) }
    }

    fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, Dokumentdistribusjon> {
        return journalføringOgBrevdistribusjon.distribuerBrev(distribuerBrev)
            .map { copy(journalføringOgBrevdistribusjon = it) }
    }
}
