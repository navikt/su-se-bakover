package no.nav.su.se.bakover.client.journalpost

import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost

internal data class SafResponse(
    val journalpost: Journalpost?,
) {
    fun toHentetJournalpost(): HentetJournalpost {
        if (journalpost == null) throw IllegalStateException("journalpostId skal ikke være null på dette tidspunktet")
        return HentetJournalpost.create(journalpost.tema)
    }
}

internal data class Journalpost(
    val tema: String,
)
