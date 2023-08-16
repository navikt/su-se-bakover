package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.client.dokarkiv.JournalpostDokument.Companion.lagDokumenterForJournalpost
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Behandlingstema
import no.nav.su.se.bakover.domain.Tema
import no.nav.su.se.bakover.domain.brev.jsonRequest.PdfInnhold
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import java.time.LocalDate
import java.util.Base64

sealed class Journalpost {
    val tema: String = Tema.SUPPLERENDE_STØNAD.value
    abstract val sakstype: Sakstype
    abstract val journalfoerendeEnhet: JournalførendeEnhet
    abstract val tittel: String
    abstract val journalpostType: JournalPostType
    abstract val kanal: String?
    abstract val avsenderMottaker: AvsenderMottaker?
    abstract val bruker: Bruker
    abstract val sak: Fagsak
    abstract val dokumenter: List<JournalpostDokument>
    abstract val person: Person
    abstract val saksnummer: Saksnummer
    abstract val datoDokument: LocalDate?

    val behandlingstema: String
        get() = when (sakstype) {
            Sakstype.ALDER -> Behandlingstema.SU_ALDER.value
            Sakstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING.value
        }

    fun søkersNavn(person: Person): String =
        """${person.navn.etternavn}, ${person.navn.fornavn} ${person.navn.mellomnavn ?: ""}""".trimEnd()

    data class Søknadspost private constructor(
        override val person: Person,
        override val saksnummer: Saksnummer,
        override val sakstype: Sakstype,
        override val dokumenter: List<JournalpostDokument>,
    ) : Journalpost() {
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person),
        )
        override val tittel: String = lagTittel(sakstype)
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val sak: Fagsak = Fagsak(saksnummer.nummer.toString())
        override val journalpostType: JournalPostType = JournalPostType.INNGAAENDE
        override val kanal: String = "INNSENDT_NAV_ANSATT"
        override val journalfoerendeEnhet: JournalførendeEnhet = JournalførendeEnhet.AUTOMATISK
        override val datoDokument: LocalDate? get() = null

        companion object {
            fun lagTittel(sakstype: Sakstype) = when (sakstype) {
                Sakstype.ALDER -> "Søknad om supplerende stønad for alder"
                Sakstype.UFØRE -> "Søknad om supplerende stønad for uføre flyktninger"
            }

            fun from(
                person: Person,
                saksnummer: Saksnummer,
                søknadInnhold: SøknadInnhold,
                pdf: PdfA,
            ): Søknadspost = Søknadspost(
                person = person,
                saksnummer = saksnummer,
                dokumenter = lagDokumenterForJournalpost(
                    pdf = pdf,
                    søknadInnhold = søknadInnhold,
                ),
                sakstype = søknadInnhold.type(),
            )
        }
    }

    data class Vedtakspost private constructor(
        override val person: Person,
        override val saksnummer: Saksnummer,
        override val dokumenter: List<JournalpostDokument>,
        override val tittel: String,
        override val sakstype: Sakstype,
    ) : Journalpost() {
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person),
        )
        override val sak: Fagsak = Fagsak(saksnummer.nummer.toString())
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val journalpostType: JournalPostType = JournalPostType.UTGAAENDE
        override val kanal: String? = null
        override val journalfoerendeEnhet: JournalførendeEnhet = JournalførendeEnhet.ÅLESUND
        override val datoDokument: LocalDate? get() = null

        companion object {
            fun from(
                person: Person,
                saksnummer: Saksnummer,
                pdfInnhold: PdfInnhold,
                pdf: PdfA,
                sakstype: Sakstype,
            ) = Vedtakspost(
                person = person,
                saksnummer = saksnummer,
                dokumenter = lagDokumenterForJournalpost(
                    tittel = pdfInnhold.pdfTemplate.tittel(),
                    pdf = pdf,
                    originalJson = pdfInnhold.toJson(),
                ),
                tittel = pdfInnhold.pdfTemplate.tittel(),
                sakstype = sakstype,
            )

            fun from(
                person: Person,
                saksnummer: Saksnummer,
                dokument: Dokument,
                sakstype: Sakstype,
            ) = Vedtakspost(
                person = person,
                saksnummer = saksnummer,
                dokumenter = lagDokumenterForJournalpost(
                    tittel = dokument.tittel,
                    pdf = dokument.generertDokument,
                    originalJson = dokument.generertDokumentJson,
                ),
                tittel = dokument.tittel,
                sakstype = sakstype,
            )
        }
    }

    data class Info private constructor(
        override val person: Person,
        override val saksnummer: Saksnummer,
        override val dokumenter: List<JournalpostDokument>,
        override val tittel: String,
        override val sakstype: Sakstype,
    ) : Journalpost() {
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person),
        )
        override val sak: Fagsak = Fagsak(saksnummer.nummer.toString())
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val journalpostType: JournalPostType = JournalPostType.UTGAAENDE
        override val kanal: String? = null
        override val journalfoerendeEnhet: JournalførendeEnhet = JournalførendeEnhet.ÅLESUND
        override val datoDokument: LocalDate? get() = null

        companion object {
            fun from(
                sakInfo: SakInfo,
                person: Person,
                tittel: String,
                pdf: PdfA,
                originalDokumentJson: String,
            ) = Info(
                person = person,
                saksnummer = sakInfo.saksnummer,
                dokumenter = lagDokumenterForJournalpost(
                    tittel = tittel,
                    pdf = pdf,
                    originalJson = originalDokumentJson,
                ),
                tittel = tittel,
                sakstype = sakInfo.type,
            )

            fun from(
                person: Person,
                saksnummer: Saksnummer,
                pdfInnhold: PdfInnhold,
                pdf: PdfA,
                sakstype: Sakstype,
            ) = Info(
                person = person,
                saksnummer = saksnummer,
                dokumenter = lagDokumenterForJournalpost(
                    tittel = pdfInnhold.pdfTemplate.tittel(),
                    pdf = pdf,
                    originalJson = pdfInnhold.toJson(),
                ),
                tittel = pdfInnhold.pdfTemplate.tittel(),
                sakstype = sakstype,
            )

            fun from(
                person: Person,
                saksnummer: Saksnummer,
                dokument: Dokument,
                sakstype: Sakstype,
            ) = Info(
                person = person,
                saksnummer = saksnummer,
                dokumenter = lagDokumenterForJournalpost(
                    tittel = dokument.tittel,
                    pdf = dokument.generertDokument,
                    originalJson = dokument.generertDokumentJson,
                ),
                tittel = dokument.tittel,
                sakstype = sakstype,
            )
        }
    }
}

internal data class JournalpostRequest(
    val tittel: String,
    val journalpostType: JournalPostType,
    val tema: String,
    val kanal: String?,
    val behandlingstema: String,
    val journalfoerendeEnhet: String,
    val avsenderMottaker: AvsenderMottaker?,
    val bruker: Bruker,
    val sak: Fagsak,
    val dokumenter: List<JournalpostDokument>,
)

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

data class JournalpostDokument(
    val tittel: String,
    val brevkode: String = "XX.YY-ZZ",
    val dokumentvarianter: List<DokumentVariant>,
) {
    companion object {
        internal fun lagDokumenterForJournalpost(
            pdf: PdfA,
            søknadInnhold: SøknadInnhold,
        ): List<JournalpostDokument> = lagDokumenterForJournalpost(
            tittel = Journalpost.Søknadspost.lagTittel(søknadInnhold.type()),
            pdf = pdf,
            originalJson = serialize(søknadInnhold),
        )

        internal fun lagDokumenterForJournalpost(
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
