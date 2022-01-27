package no.nav.su.se.bakover.domain.journalpost

import no.nav.su.se.bakover.domain.Saksnummer

/**
 * Representerer en journalpost som er hentet fra SAF
 *
 * TODO - Gjøre noe fine ting mellom denne og OpprettNyJournalpost
 */
data class HentetJournalpost private constructor(
    private val tema: String,
    private val sak: Fagsak,
) {

    fun erJournalpostKnyttetTilSak(saksnummer: Saksnummer): Boolean {
        return erTemaSup() && erfagSakIdLikSaksnummer(saksnummer)
    }

    private fun erTemaSup(): Boolean {
        return tema == "SUP"
    }

    private fun erfagSakIdLikSaksnummer(saksnummer: Saksnummer): Boolean {
        return sak.fagsakId == saksnummer.toString()
    }

    companion object {
        fun create(tema: String, sak: Fagsak): HentetJournalpost {
            return HentetJournalpost(tema, sak)
        }
    }
}

data class Fagsak(
    val fagsakId: String,
    val fagsaksystem: String,
    val sakstype: String,
    val tema: String,
    val datoOpprettet: String,
)
