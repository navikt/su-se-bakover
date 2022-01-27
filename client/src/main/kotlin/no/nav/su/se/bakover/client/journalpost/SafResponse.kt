package no.nav.su.se.bakover.client.journalpost

import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost

internal data class SafResponse(
    val journalpost: Journalpost,
) {
    fun toHentetJournalpost(): HentetJournalpost {
        return HentetJournalpost.create(journalpost.tema, journalpost.sak.toDomainFagsak())
    }
}

internal data class Journalpost(
    val tema: String,
    val sak: Fagsak,
)

internal data class Fagsak(
    val fagsakId: String,
    val fagsaksystem: String,
    val sakstype: String,
    val tema: String,
    val datoOpprettet: String,
) {
    fun toDomainFagsak(): no.nav.su.se.bakover.domain.journalpost.Fagsak {
        return no.nav.su.se.bakover.domain.journalpost.Fagsak(
            fagsakId = fagsakId,
            fagsaksystem = fagsaksystem,
            sakstype = sakstype,
            tema = tema,
            datoOpprettet = datoOpprettet,
        )
    }
}
