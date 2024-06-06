package dokument.domain.distribuering

import dokument.domain.brev.BrevbestillingId
import dokument.domain.brev.KunneIkkeBestilleBrevForDokument
import no.nav.su.se.bakover.common.journal.JournalpostId
import java.util.UUID

sealed interface KunneIkkeDistribuereJournalførtDokument {
    data class AlleredeDistribuert(
        val dokumentId: UUID,
        val journalpostId: JournalpostId,
        val brevbestillingId: BrevbestillingId,
    ) : KunneIkkeDistribuereJournalførtDokument

    data class IkkeJournalført(
        val dokumentId: UUID,
    ) : KunneIkkeDistribuereJournalførtDokument

    data class FantIkkeDokument(
        val dokumentId: UUID,
    ) : KunneIkkeDistribuereJournalførtDokument

    data class FeilVedDistribusjon(
        val dokumentId: UUID,
        val journalpostId: JournalpostId,
        val underliggendeFeil: KunneIkkeBestilleBrevForDokument,
    ) : KunneIkkeDistribuereJournalførtDokument

    data class IkkeTilgang(val dokumentId: UUID) : KunneIkkeDistribuereJournalførtDokument
}
