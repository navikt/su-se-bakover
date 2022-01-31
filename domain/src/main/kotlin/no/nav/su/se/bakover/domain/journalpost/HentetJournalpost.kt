package no.nav.su.se.bakover.domain.journalpost

import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.domain.Saksnummer

// TODO - Gjøre noe fine ting mellom denne og eksisterende journalpost
data class HentetJournalpost private constructor(
    private val tema: String,
    private val journalstatus: String,
    private val sak: Sak,
) {
    fun validerJournalpost(saksnummer: Saksnummer): Boolean {
        return erTemaSup() && erFerdigstilt() && erKnyttetTilSak(saksnummer)
    }

    private fun erTemaSup(): Boolean {
        return tema == "SUP"
    }

    private fun erFerdigstilt(): Boolean {
        return journalstatus == "FERDIGSTILT"
    }

    private fun erKnyttetTilSak(saksnummer: Saksnummer): Boolean {
        // hopper over denne sjekken når vi er lokalt siden vi ikke lett kan sjekke et tilfeldig saksnummer, mot journalpost-stubben
        if (ApplicationConfig.isRunningLocally()) {
            return true
        }
        return saksnummer.toString() == sak.fagsakId
    }

    companion object {
        fun create(
            tema: String,
            journalstatus: String,
            sak: Sak,
        ): HentetJournalpost {
            return HentetJournalpost(tema, journalstatus, sak)
        }
    }
}

data class Sak(
    val fagsakId: String,
)
