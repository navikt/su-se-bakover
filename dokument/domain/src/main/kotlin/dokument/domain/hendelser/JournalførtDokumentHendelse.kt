package dokument.domain.hendelser

import no.nav.su.se.bakover.common.journal.JournalpostId

sealed interface JournalførtDokumentHendelse : DokumentHendelse {
    val journalpostId: JournalpostId
}
