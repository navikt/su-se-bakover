package no.nav.su.se.bakover.client.saf

import arrow.core.Either
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.domain.journal.JournalpostId

/**
 * Saf = Sak og arkiv fasade
 */
interface SafClient {
    fun hentJournalpost(journalpostId: JournalpostId): Either<KunneIkkeHenteJournalpost, Journalpost>
}

sealed interface KunneIkkeHenteJournalpost {
    object Ukjent : KunneIkkeHenteJournalpost
}
