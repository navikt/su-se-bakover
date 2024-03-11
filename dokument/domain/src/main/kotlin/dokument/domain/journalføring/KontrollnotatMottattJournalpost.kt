package dokument.domain.journalføring

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
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
