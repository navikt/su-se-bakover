package no.nav.su.se.bakover.domain.journalpost

// TODO - Gjøre noe fine ting mellom denne og eksisterende journalpost
data class HentetJournalpost private constructor(
    private val tema: String,
) {
    fun validerJournalpost(): Boolean {
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
