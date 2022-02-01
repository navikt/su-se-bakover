package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import no.nav.su.se.bakover.domain.journalpost.Tema

internal data class JournalpostResponse(
    val journalpost: Journalpost?,
) {
    fun toHentetJournalpost(saksnummer: Saksnummer): Either<KunneIkkeHenteJournalpost, HentetJournalpost> {
        if (journalpost == null) {
            return KunneIkkeHenteJournalpost.FantIkkeJournalpost.left()
        }
        if (journalpost.tema != Tema.SUP.toString()) {
            return KunneIkkeHenteJournalpost.JournalpostTemaErIkkeSUP.left()
        }

        if (journalpost.journalstatus != JournalpostStatus.FERDIGSTILT.toString()) {
            return KunneIkkeHenteJournalpost.JournalpostenErIkkeFerdigstilt.left()
        }

        if (saksnummer.nummer.toString() != journalpost.sak.fagsakId) {
            return KunneIkkeHenteJournalpost.JournalpostIkkeKnyttetTilSak.left()
        }

        return HentetJournalpost.create(
            Tema.valueOf(journalpost.tema),
            JournalpostStatus.valueOf(journalpost.journalstatus),
            saksnummer,
        ).right()
    }
}

internal data class Journalpost(
    val tema: String,
    val journalstatus: String,
    val sak: Sak,
)

internal data class Sak(
    val fagsakId: String,
)
