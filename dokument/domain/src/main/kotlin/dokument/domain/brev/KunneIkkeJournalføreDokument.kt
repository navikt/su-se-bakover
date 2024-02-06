package dokument.domain.brev

sealed interface KunneIkkeJournalføreDokument {
    data object KunneIkkeFinnePerson : KunneIkkeJournalføreDokument
    data object FeilVedOpprettelseAvJournalpost : KunneIkkeJournalføreDokument
}
