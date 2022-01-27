package no.nav.su.se.bakover.domain.journalpost

import arrow.core.Either
import no.nav.su.se.bakover.domain.journal.JournalpostId

interface JournalpostClient {
    fun hentJournalpost(journalpostId: JournalpostId): Either<KunneIkkeHenteJournalpost, HentetJournalpost>
}

sealed interface KunneIkkeHenteJournalpost {
    object Ukjent : KunneIkkeHenteJournalpost
    object FantIkkeJournalpost : KunneIkkeHenteJournalpost
    object IkkeTilgang : KunneIkkeHenteJournalpost
    object TekniskFeil : KunneIkkeHenteJournalpost
}
