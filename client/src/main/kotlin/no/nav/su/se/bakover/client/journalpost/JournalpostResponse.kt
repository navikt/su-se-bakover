package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost

internal data class JournalpostResponse(
    val journalpost: Journalpost?,
) {
    fun toHentetJournalpost(): Either<JournalpostFinnesIkke, HentetJournalpost> {
        if (journalpost == null) {
            return JournalpostFinnesIkke.left()
        }
        return HentetJournalpost.create(
            journalpost.tema,
            journalpost.journalstatus,
            no.nav.su.se.bakover.domain.journalpost.Sak(journalpost.sak.fagsakId),
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

object JournalpostFinnesIkke
