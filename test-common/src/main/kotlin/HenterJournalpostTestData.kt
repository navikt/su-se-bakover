package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.Tema

fun getHentetJournalpost(): FerdigstiltJournalpost {
    return FerdigstiltJournalpost.create(
        tema = Tema.SUP,
        journalstatus = JournalpostStatus.FERDIGSTILT,
        saksnummer = Saksnummer(2021),
    )
}
