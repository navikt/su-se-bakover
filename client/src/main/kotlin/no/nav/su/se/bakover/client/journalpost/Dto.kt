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

/**
 * https://confluence.adeo.no/display/BOA/Type%3A+Sak
 */
internal data class Sak(
    val fagsakId: String? = null,
)

/**
 * Generell modell for spørringer mot dokumentoversiktFagsak
 */
internal data class HentDokumentoversiktFagsakHttpResponse(
    override val data: HentDokumentoversiktFagsakResponse?,
    override val errors: List<Error>?,
) : GraphQLHttpResponse()

internal data class HentDokumentoversiktFagsakResponse(
    val dokumentoversiktFagsak: DokumentoversiktFagsak,
)

internal data class DokumentoversiktFagsak(
    val journalposter: List<Journalpost>,
)

data class Fagsak(
    val fagsakId: String,
    val fagsaksystem: String = "SUPSTONAD",
)

/**
 * Variabler for spørringer mot dokumentoversiktFagsak
 * Fyll ut det som er nødvendig for din spørring
 */
internal data class HentJournalposterForSakVariables(
    val fagsak: Fagsak,
    val fraDato: String? = null,
    val tema: String = "SUP",
    val journalposttyper: List<String> = emptyList(),
    val journalstatuser: List<String> = emptyList(),
    val foerste: Int = 50,
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

internal enum class JournalpostTema {
    SUP,
}

internal fun JournalpostTema.toDomain(): dokument.domain.journalføring.JournalpostTema {
    return when (this) {
        JournalpostTema.SUP -> dokument.domain.journalføring.JournalpostTema.SUP
    }
}

internal enum class JournalpostStatus {
    JOURNALFOERT,
    FERDIGSTILT,
}

internal fun JournalpostStatus.toDomain(): dokument.domain.journalføring.JournalpostStatus {
    return when (this) {
        JournalpostStatus.JOURNALFOERT -> dokument.domain.journalføring.JournalpostStatus.JOURNALFOERT
        JournalpostStatus.FERDIGSTILT -> dokument.domain.journalføring.JournalpostStatus.FERDIGSTILT
    }
}

internal enum class JournalpostType {
    // Innkommende dokument
    I,
}

internal fun JournalpostType.toDomain(): dokument.domain.journalføring.JournalpostType {
    return when (this) {
        JournalpostType.I -> dokument.domain.journalføring.JournalpostType.INNKOMMENDE_DOKUMENT
    }
}
