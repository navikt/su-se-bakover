package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Behandlingstema
import no.nav.su.se.bakover.domain.Tema
import no.nav.su.se.bakover.domain.journalpost.JournalpostCommand
import no.nav.su.se.bakover.domain.journalpost.JournalpostForSakCommand
import no.nav.su.se.bakover.domain.journalpost.JournalpostSkattForSak
import no.nav.su.se.bakover.domain.journalpost.JournalpostSkattUtenforSak
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.Sakstype
import java.time.LocalDate
import java.util.Base64

internal data class JournalpostRequest(
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
    val datoDokument: LocalDate?,
)

private fun søkersNavn(navn: Person.Navn): String =
    """${navn.etternavn}, ${navn.fornavn} ${navn.mellomnavn ?: ""}""".trimEnd()

internal fun JournalpostCommand.tilJson(): String {
    val bruker = Bruker(id = fnr.toString())
    val behandlingstema = when (sakstype) {
        Sakstype.ALDER -> Behandlingstema.SU_ALDER.value
        Sakstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING.value
    }
    return when (this) {
        is JournalpostSkattForSak -> JournalpostRequest(
            tittel = dokument.dokumentTittel,
            journalpostType = JournalPostType.NOTAT,
            kanal = null,
            behandlingstema = behandlingstema,
            journalfoerendeEnhet = JournalførendeEnhet.AUTOMATISK.enhet,
            avsenderMottaker = null,
            bruker = bruker,
            sak = Fagsak(saksnummer.nummer.toString()),
            dokumenter = JournalpostDokument.lagDokumenterForJournalpostForSak(
                tittel = dokument.dokumentTittel,
                pdf = dokument.generertDokument,
                originalJson = dokument.dokumentJson,
            ),
            datoDokument = dokument.skattedataHentet.toLocalDate(zoneIdOslo),
        )

        is JournalpostForSakCommand.Brev -> JournalpostRequest(
            tittel = dokument.tittel,
            journalpostType = JournalPostType.UTGAAENDE,
            kanal = null,
            behandlingstema = behandlingstema,
            journalfoerendeEnhet = JournalførendeEnhet.ÅLESUND.enhet,
            avsenderMottaker = AvsenderMottaker(id = fnr.toString(), navn = søkersNavn(this.navn)),
            bruker = bruker,
            sak = Fagsak(saksnummer.nummer.toString()),
            dokumenter = JournalpostDokument.lagDokumenterForJournalpostForSak(
                tittel = dokument.tittel,
                pdf = dokument.generertDokument,
                originalJson = dokument.generertDokumentJson,
            ),
            datoDokument = dokument.opprettet.toLocalDate(zoneIdOslo),
        )

        is JournalpostForSakCommand.Søknadspost -> JournalpostRequest(
            tittel = when (sakstype) {
                Sakstype.ALDER -> "Søknad om supplerende stønad for alder"
                Sakstype.UFØRE -> "Søknad om supplerende stønad for uføre flyktninger"
            },
            journalpostType = JournalPostType.INNGAAENDE,
            kanal = Kanal.INNSENDT_NAV_ANSTATT.value,
            behandlingstema = behandlingstema,
            journalfoerendeEnhet = JournalførendeEnhet.AUTOMATISK.enhet,
            avsenderMottaker = AvsenderMottaker(id = fnr.toString(), navn = søkersNavn(this.navn)),
            bruker = bruker,
            sak = Fagsak(saksnummer.nummer.toString()),
            dokumenter = JournalpostDokument.lagDokumenterForJournalpostForSak(
                tittel = when (sakstype) {
                    Sakstype.ALDER -> "Søknad om supplerende stønad for alder"
                    Sakstype.UFØRE -> "Søknad om supplerende stønad for uføre flyktninger"
                },
                pdf = pdf,
                originalJson = serialize(søknadInnhold),
            ),
            datoDokument = datoDokument,
        )

        is JournalpostSkattUtenforSak -> JournalpostRequest(
            tittel = dokument.tittel,
            journalpostType = JournalPostType.NOTAT,
            kanal = null,
            behandlingstema = behandlingstema,
            journalfoerendeEnhet = JournalførendeEnhet.AUTOMATISK.enhet,
            avsenderMottaker = null,
            bruker = bruker,
            sak = Fagsak(this.fagsystemId),
            dokumenter = JournalpostDokument.lagDokumenterForJournalpostForSak(
                tittel = dokument.tittel,
                pdf = dokument.generertDokument,
                originalJson = dokument.generertDokumentJson,
            ),
            datoDokument = dokument.opprettet.toLocalDate(zoneIdOslo),
        )
    }.let {
        serialize(it)
    }
}

private enum class Kanal(val value: String) {
    /**
     * Dette er en Mottakskanal - til bruk for inngående dokumenter
     */
    INNSENDT_NAV_ANSTATT("INNSENDT_NAV_ANSATT"),
}

internal data class JournalpostDokument(
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
    val navn: String,
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

sealed class DokumentVariant {
    abstract val filtype: String
    abstract val fysiskDokument: String
    abstract val variantformat: String

    data class ArkivPDF(
        override val fysiskDokument: String,
    ) : DokumentVariant() {
        override val filtype: String = "PDFA"
        override val variantformat: String = "ARKIV"
    }

    data class OriginalJson(
        override val fysiskDokument: String,
    ) : DokumentVariant() {
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
