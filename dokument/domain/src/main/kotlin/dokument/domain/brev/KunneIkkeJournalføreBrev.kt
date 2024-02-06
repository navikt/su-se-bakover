package dokument.domain.brev

sealed interface KunneIkkeJournalføreBrev {

    data object KunneIkkeOppretteJournalpost : KunneIkkeJournalføreBrev
}
