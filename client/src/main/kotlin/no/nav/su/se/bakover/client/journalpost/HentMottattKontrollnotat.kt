package no.nav.su.se.bakover.client.journalpost

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.KontrollnotatMottattJournalpost

internal fun List<Journalpost>.toDomain(): List<KontrollnotatMottattJournalpost> {
    return map { it.toDomain() }
}

/**
 * Forventer at alle data er tilgjengelig basert på spørringen som utføres [HentDokumentoversiktFagsakHttpResponse]
 * og filtre [HentJournalpostForFagsakVariables] som sendes med.
 */
internal fun Journalpost.toDomain(): KontrollnotatMottattJournalpost {
    return KontrollnotatMottattJournalpost(
        tema = no.nav.su.se.bakover.client.journalpost.JournalpostTema.valueOf(tema!!).toDomain(),
        journalstatus = no.nav.su.se.bakover.client.journalpost.JournalpostStatus.valueOf(journalstatus!!).toDomain(),
        journalposttype = no.nav.su.se.bakover.client.journalpost.JournalpostType.valueOf(journalposttype!!).toDomain(),
        saksnummer = Saksnummer(sak!!.fagsakId!!.toLong()),
        tittel = tittel!!,
        datoOpprettet = datoOpprettet!!,
        journalpostId = JournalpostId(journalpostId!!),
    )
}
