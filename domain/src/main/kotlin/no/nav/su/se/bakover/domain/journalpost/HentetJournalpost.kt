package no.nav.su.se.bakover.domain.journalpost

/**
 * Representerer en journalpost som er hentet fra SAF
 *
 * TODO - Gjøre noe fine ting mellom denne og OpprettNyJournalpost
 */
data class HentetJournalpost private constructor(
    private val tema: String,

) {

    fun erJournalpostKnyttetTilSak(): Boolean {
        return erTemaSup()
    }

    private fun erTemaSup(): Boolean {
        return tema == "SUP"
    }

    companion object {
        fun create(
            tema: String,
        ): HentetJournalpost {
            return HentetJournalpost(tema)
        }
    }
}
