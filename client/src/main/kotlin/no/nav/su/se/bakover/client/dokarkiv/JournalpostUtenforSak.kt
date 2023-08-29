package no.nav.su.se.bakover.client.dokarkiv

sealed interface JournalpostUtenforSak : Journalpost {
    val saksnummer: String
    override val avsenderMottaker: AvsenderMottaker? get() = null
    override val kanal: String? get() = null
}
