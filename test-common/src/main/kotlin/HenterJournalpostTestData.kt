package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostTema
import no.nav.su.se.bakover.domain.journalpost.JournalpostType

fun getHentetJournalpost(): FerdigstiltJournalpost {
    return FerdigstiltJournalpost(
        tema = JournalpostTema.SUP,
        journalstatus = JournalpostStatus.JOURNALFOERT,
        journalposttype = JournalpostType.INNKOMMENDE_DOKUMENT,
        saksnummer = Saksnummer(2021),
    )
}
