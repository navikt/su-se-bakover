package no.nav.su.se.bakover.domain.journalpost

import arrow.core.Either
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId

interface JournalpostClient {
    fun hentFerdigstiltJournalpost(
        saksnummer: Saksnummer,
        journalpostId: JournalpostId,
    ): Either<KunneIkkeHenteJournalpost, FerdigstiltJournalpost>
}

sealed interface KunneIkkeHenteJournalpost {
    object Ukjent : KunneIkkeHenteJournalpost
    object FantIkkeJournalpost : KunneIkkeHenteJournalpost
    object IkkeTilgang : KunneIkkeHenteJournalpost
    object TekniskFeil : KunneIkkeHenteJournalpost
    object UgyldigInput : KunneIkkeHenteJournalpost
    object JournalpostIkkeKnyttetTilSak : KunneIkkeHenteJournalpost
    object JournalpostTemaErIkkeSUP : KunneIkkeHenteJournalpost
    object JournalpostenErIkkeFerdigstilt : KunneIkkeHenteJournalpost
}
