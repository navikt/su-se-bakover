package no.nav.su.se.bakover.domain.journalpost

sealed interface KunneIkkeLageJournalpostUtenforSak {
    data object FagsystemIdErTom : KunneIkkeLageJournalpostUtenforSak
}
