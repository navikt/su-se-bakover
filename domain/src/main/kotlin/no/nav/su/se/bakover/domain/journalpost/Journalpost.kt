package no.nav.su.se.bakover.domain.journalpost

import no.nav.su.se.bakover.domain.Saksnummer
import java.time.LocalDate

data class FerdigstiltJournalpost(
    val tema: JournalpostTema,
    val journalstatus: JournalpostStatus,
    val journalposttype: JournalpostType,
    val saksnummer: Saksnummer,
) {
    init {
        require(tema == JournalpostTema.SUP)
        require(journalposttype == JournalpostType.INNKOMMENDE_DOKUMENT)
        require(journalstatus == JournalpostStatus.JOURNALFOERT)
    }
}

data class KontrollnotatMottattJournalpost(
    val tema: JournalpostTema,
    val journalstatus: JournalpostStatus,
    val journalposttype: JournalpostType,
    val saksnummer: Saksnummer,
    val tittel: String,
    val datoOpprettet: LocalDate
)

enum class JournalpostTema {
    SUP
}

enum class JournalpostStatus {
    JOURNALFOERT,
    FERDIGSTILT;
}

enum class JournalpostType(val value: String) {
    INNKOMMENDE_DOKUMENT("I");

    companion object {
        fun fromString(value: String): JournalpostType {
            return values().find { it.value == value }
                ?: throw IllegalArgumentException("Journalposttypen $value er ikke støtte for")
        }
    }
}
