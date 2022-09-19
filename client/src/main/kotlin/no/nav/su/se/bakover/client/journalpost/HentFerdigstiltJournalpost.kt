package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostTema
import no.nav.su.se.bakover.domain.journalpost.JournalpostType

internal fun Journalpost?.toFerdigstiltJournalpost(saksnummer: Saksnummer): Either<JournalpostErIkkeFerdigstilt, FerdigstiltJournalpost> {
    if (this == null) {
        return JournalpostErIkkeFerdigstilt.FantIkkeJournalpost.left()
    }
    if (tema == null || tema != JournalpostTema.SUP.toString()) {
        return JournalpostErIkkeFerdigstilt.JournalpostTemaErIkkeSUP.left()
    }
    if (journalposttype == null || journalposttype != JournalpostType.INNKOMMENDE_DOKUMENT.value) {
        return JournalpostErIkkeFerdigstilt.JournalpostenErIkkeEtInnkommendeDokument.left()
    }

    if (journalstatus == null || journalstatus != JournalpostStatus.JOURNALFOERT.toString()) {
        return JournalpostErIkkeFerdigstilt.JournalpostenErIkkeFerdigstilt.left()
    }

    if (sak?.fagsakId == null || saksnummer.toString() != sak.fagsakId) {
        return JournalpostErIkkeFerdigstilt.JournalpostIkkeKnyttetTilSak.left()
    }

    return FerdigstiltJournalpost(
        tema = JournalpostTema.valueOf(tema),
        journalstatus = JournalpostStatus.valueOf(journalstatus),
        journalposttype = JournalpostType.fromString(journalposttype),
        saksnummer = Saksnummer(sak.fagsakId.toLong()),
    ).right()
}

sealed interface JournalpostErIkkeFerdigstilt {
    object FantIkkeJournalpost : JournalpostErIkkeFerdigstilt
    object JournalpostTemaErIkkeSUP : JournalpostErIkkeFerdigstilt
    object JournalpostenErIkkeEtInnkommendeDokument : JournalpostErIkkeFerdigstilt
    object JournalpostenErIkkeFerdigstilt : JournalpostErIkkeFerdigstilt
    object JournalpostIkkeKnyttetTilSak : JournalpostErIkkeFerdigstilt
}
