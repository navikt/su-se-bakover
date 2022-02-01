package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.Tema

fun getHentetJournalpost(): HentetJournalpost {
    return HentetJournalpost.create(
        tema = Tema.SUP,
        journalstatus = JournalpostStatus.FERDIGSTILT,
        saksnummer = Saksnummer(2021),
    )
}
