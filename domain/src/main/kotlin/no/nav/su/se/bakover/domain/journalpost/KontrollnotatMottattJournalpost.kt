package no.nav.su.se.bakover.domain.journalpost

import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.time.LocalDate

data class KontrollnotatMottattJournalpost(
    val tema: JournalpostTema,
    val journalstatus: JournalpostStatus,
    val journalposttype: JournalpostType,
    val saksnummer: Saksnummer,
    val tittel: String,
    val datoOpprettet: LocalDate,
    val journalpostId: JournalpostId,
)
