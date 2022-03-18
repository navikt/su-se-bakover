package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostType
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import no.nav.su.se.bakover.domain.journalpost.Tema

internal data class JournalpostResponse(
    val journalpost: Journalpost?,
) {
    /**
     * Denne flyten er skreddersydd for å hente innkommende, journalførte dokumenter som tilhører SUP.
     */
    fun toValidertInnkommendeJournalførtJournalpost(saksnummer: Saksnummer): Either<KunneIkkeHenteJournalpost, FerdigstiltJournalpost> {
        if (journalpost == null) {
            return KunneIkkeHenteJournalpost.FantIkkeJournalpost.left()
        }
        if (journalpost.tema == null || journalpost.tema != Tema.SUP.toString()) {
            return KunneIkkeHenteJournalpost.JournalpostTemaErIkkeSUP.left()
        }
        if (journalpost.journalposttype == null || journalpost.journalposttype != JournalpostType.INNKOMMENDE_DOKUMENT.value) {
            return KunneIkkeHenteJournalpost.JournalpostenErIkkeEtInnkommendeDokument.left()
        }

        if (journalpost.journalstatus == null || journalpost.journalstatus != JournalpostStatus.JOURNALFOERT.toString()) {
            return KunneIkkeHenteJournalpost.JournalpostenErIkkeFerdigstilt.left()
        }

        if (journalpost.sak?.fagsakId == null || saksnummer.nummer.toString() != journalpost.sak.fagsakId) {
            return KunneIkkeHenteJournalpost.JournalpostIkkeKnyttetTilSak.left()
        }

        return FerdigstiltJournalpost.create(
            Tema.valueOf(journalpost.tema),
            JournalpostStatus.valueOf(journalpost.journalstatus),
            JournalpostType.fromString(journalpost.journalposttype),
            saksnummer,
        ).right()
    }
}

internal data class Journalpost(
    val tema: String?,
    val journalstatus: String?,
    val journalposttype: String?,
    val sak: Sak?,
)

internal data class Sak(
    val fagsakId: String?,
)
