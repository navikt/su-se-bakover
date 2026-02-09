package no.nav.su.se.bakover.client.journalpost

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

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
    val tema: List<String> = listOf("SUP"),
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
 * Generell modell for spørringer mot hentJournalpost med dokumentmetadata
 */
internal data class HentJournalpostMedDokumenterHttpResponse(
    override val data: HentJournalpostMedDokumenterResponse?,
    override val errors: List<Error>?,
) : GraphQLHttpResponse()

internal data class HentJournalpostMedDokumenterResponse(
    val journalpost: JournalpostMedDokumenterResponse?,
)

internal data class JournalpostMedDokumenterResponse(
    val journalpostId: String,
    val tittel: String?,
    val datoOpprettet: LocalDate?,
    val dokumenter: List<DokumentInfoResponse> = emptyList(),
)

internal data class DokumentInfoResponse(
    val dokumentInfoId: String,
    val tittel: String?,
    val brevkode: String?,
    val dokumentstatus: String?,
    val dokumentvarianter: List<DokumentvariantResponse> = emptyList(),
)

internal data class DokumentvariantResponse(
    @JsonProperty("variantformat")
    val variantFormat: String,
    val filtype: String?,
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
