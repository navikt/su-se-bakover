package no.nav.su.se.bakover.client.journalpost

import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostTema
import no.nav.su.se.bakover.domain.journalpost.JournalpostType
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
        tema = JournalpostTema.valueOf(tema!!),
        journalstatus = JournalpostStatus.valueOf(journalstatus!!),
        journalposttype = JournalpostType.fromString(journalposttype!!),
        saksnummer = Saksnummer(sak!!.fagsakId!!.toLong()),
        tittel = tittel!!,
        datoOpprettet = datoOpprettet!!
    )
}
