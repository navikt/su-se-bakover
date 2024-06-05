package no.nav.su.se.bakover.service.journalføring

import dokument.domain.brev.KunneIkkeBestilleBrevForDokument
import dokument.domain.brev.KunneIkkeJournalføreDokument

sealed interface JournalføringOgDistribueringsFeil {
    @JvmInline
    value class Distribuering(
        val originalFeil: KunneIkkeBestilleBrevForDokument,
    ) : JournalføringOgDistribueringsFeil

    @JvmInline
    value class Journalføring(val originalFeil: KunneIkkeJournalføreDokument) : JournalføringOgDistribueringsFeil
}
