package dokument.domain.brev

sealed interface KunneIkkeBestilleBrevForDokument {

    data object FeilVedBestillingAvBrev : KunneIkkeBestilleBrevForDokument
    data object MåJournalføresFørst : KunneIkkeBestilleBrevForDokument
    data object ForTidligÅPrøvePåNytt : KunneIkkeBestilleBrevForDokument
}
