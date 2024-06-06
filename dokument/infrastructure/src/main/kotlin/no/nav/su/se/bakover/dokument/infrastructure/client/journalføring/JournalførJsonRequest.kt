package no.nav.su.se.bakover.dokument.infrastructure.client.journalføring

import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.Base64

data class JournalførJsonRequest(
    val tittel: String,
    val journalpostType: JournalPostType,
    val tema: String = Tema.SUPPLERENDE_STØNAD.value,
    val kanal: String?,
    val behandlingstema: String,
    val journalfoerendeEnhet: String,
    val avsenderMottaker: AvsenderMottaker?,
    val bruker: Bruker,
    val sak: Fagsak,
    val dokumenter: List<JournalpostDokument>,
    val datoDokument: Tidspunkt?,
    val eksternReferanseId: String,
)

enum class Kanal(val value: String) {
    /**
     * Dette er en Mottakskanal - til bruk for inngående dokumenter
     */
    INNSENDT_NAV_ANSATT("INNSENDT_NAV_ANSATT"),
}

data class JournalpostDokument(
    val tittel: String,
    val brevkode: String = "XX.YY-ZZ",
    val dokumentvarianter: List<DokumentVariant>,
) {
    companion object {
        fun lagDokumenterForJournalpostForSak(
            tittel: String,
            pdf: PdfA,
            originalJson: String,
        ): List<JournalpostDokument> =
            listOf(
                JournalpostDokument(
                    tittel = tittel,
                    dokumentvarianter = listOf(
                        DokumentVariant.ArkivPDF(Base64.getEncoder().encodeToString(pdf.getContent())),
                        DokumentVariant.OriginalJson(Base64.getEncoder().encodeToString(originalJson.toByteArray())),
                    ),
                ),
            )
    }
}

data class AvsenderMottaker(
    val id: String,
    val idType: String = "FNR",
)

data class Bruker(
    val id: String,
    val idType: String = "FNR",
)

data class Fagsak(
    val fagsakId: String,
    val fagsaksystem: String = "SUPSTONAD",
    val sakstype: String = "FAGSAK",
)

sealed interface DokumentVariant {
    val filtype: String
    val fysiskDokument: String
    val variantformat: String

    data class ArkivPDF(
        override val fysiskDokument: String,
    ) : DokumentVariant {
        override val filtype: String = "PDFA"
        override val variantformat: String = "ARKIV"
    }

    data class OriginalJson(
        override val fysiskDokument: String,
    ) : DokumentVariant {
        override val filtype: String = "JSON"
        override val variantformat: String = "ORIGINAL"
    }
}

enum class JournalPostType(val type: String) {
    /**
     * dokumenter som kommer inn til nav (for eksempel søknad)
     */
    INNGAAENDE("INNGAAENDE"),

    /**
     * dokumenter som går ut av nav (for eksempel vedtaksbrev)
     */
    UTGAAENDE("UTGAAENDE"),

    /**
     * dokumenter som holdes i nav (for eksempel samtalereferater)
     */
    NOTAT("NOTAT"),
}

enum class JournalførendeEnhet(val enhet: String) {
    ÅLESUND("4815"),
    AUTOMATISK("9999"),
}
