package no.nav.su.se.bakover.client.journalpost

import java.time.LocalDate

/**
 * Generell modell for representasjon av en journalpost i APIet.
 * Merk at alle felter er nullable da vi selv styrer hvilke data vi vil ha i retur vha. graphql
 */
internal data class Journalpost(
    val tema: String? = null,
    val journalstatus: String? = null,
    val journalposttype: String? = null,
    val sak: Sak? = null,
    val tittel: String? = null,
    val datoOpprettet: LocalDate? = null,
    val journalpostId: String? = null,
)

internal data class Sak(
    val fagsakId: String? = null,
)

/**
 * Generell modell for spørringer mot dokumentoversiktFagsak
 */
internal data class HentDokumentoversiktFagsakHttpResponse(
    override val data: HentDokumentoversiktFagsakResponse?,
    override val errors: List<Error>?
) : GraphQLHttpResponse()

internal data class HentDokumentoversiktFagsakResponse(
    val dokumentoversiktFagsak: DokumentoversiktFagsak
)

internal data class DokumentoversiktFagsak(
    val journalposter: List<Journalpost>
)

/**
 * Variabler for spørringer mot dokumentoversiktFagsak
 */
internal data class HentJournalpostForFagsakVariables(
    val fagsakId: String,
    val fagsaksystem: String,
    val fraDato: String,
    val tema: String,
    val journalposttyper: List<String>,
    val journalstatuser: List<String>,
    val foerste: Int,
)

/**
 * Generell modell for spørringer mot hentJournalpost
 */
internal data class HentJournalpostHttpResponse(
    override val data: HentJournalpostResponse?,
    override val errors: List<Error>?,
) : GraphQLHttpResponse()

internal data class HentJournalpostResponse(
    val journalpost: Journalpost?,
)

/**
 * Variabler for spørringer mot hentJournalpost
 */
internal data class HentJournalpostVariables(
    val journalpostId: String,
)
