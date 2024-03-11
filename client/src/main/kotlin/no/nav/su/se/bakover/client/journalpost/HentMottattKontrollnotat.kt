package no.nav.su.se.bakover.client.journalpost

import dokument.domain.journalføring.KontrollnotatMottattJournalpost
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId

internal fun List<Journalpost>.toDomain(): List<KontrollnotatMottattJournalpost> {
    return map { it.toDomain() }
}

/**
 * Forventer at alle data er tilgjengelig basert på spørringen som utføres [HentDokumentoversiktFagsakHttpResponse]
 * og filtre [HentJournalpostForFagsakVariables] som sendes med.
 */
internal fun Journalpost.toDomain(): KontrollnotatMottattJournalpost {
    return KontrollnotatMottattJournalpost(
        tema = JournalpostTema.valueOf(tema!!).toDomain(),
        journalstatus = JournalpostStatus.valueOf(journalstatus!!).toDomain(),
        journalposttype = JournalpostType.valueOf(journalposttype!!).toDomain(),
        saksnummer = Saksnummer(sak!!.fagsakId!!.toLong()),
        tittel = tittel!!,
        datoOpprettet = datoOpprettet!!,
        journalpostId = JournalpostId(journalpostId!!),
    )
}
