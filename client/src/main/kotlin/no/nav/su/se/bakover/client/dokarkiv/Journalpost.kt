package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Behandlingstema
import no.nav.su.se.bakover.domain.Tema
import no.nav.su.se.bakover.domain.brev.PdfInnhold
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
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

        companion object {
            fun lagTittel(sakstype: Sakstype) = when (sakstype) {
                Sakstype.ALDER -> "Søknad om supplerende stønad for alder"
                Sakstype.UFØRE -> "Søknad om supplerende stønad for uføre flyktninger"
            }

            fun from(
                person: Person,
                saksnummer: Saksnummer,
                søknadInnhold: SøknadInnhold,
                pdf: ByteArray,
            ): Søknadspost = Søknadspost(
                person = person,
                saksnummer = saksnummer,
                dokumenter = lagDokumenter(
                    pdf = pdf,
                    søknadInnhold = søknadInnhold,
                ),
                sakstype = søknadInnhold.type(),
            )

            private fun lagDokumenter(pdf: ByteArray, søknadInnhold: SøknadInnhold): List<JournalpostDokument> =
                listOf(
                    JournalpostDokument(
                        tittel = lagTittel(søknadInnhold.type()),
                        dokumentvarianter = listOf(
                            DokumentVariant.ArkivPDF(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                            DokumentVariant.OriginalJson(
                                fysiskDokument = Base64.getEncoder()
                                    .encodeToString(objectMapper.writeValueAsString(søknadInnhold).toByteArray()),
                            ),
                        ),
                    ),
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

        companion object {
            fun from(
                person: Person,
                saksnummer: Saksnummer,
                pdfInnhold: PdfInnhold,
                pdf: ByteArray,
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

        companion object {
            fun from(
                sakInfo: SakInfo,
                person: Person,
                tittel: String,
                pdf: ByteArray,
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
                pdf: ByteArray,
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

internal fun lagDokumenterForJournalpost(tittel: String, pdf: ByteArray, originalJson: String): List<JournalpostDokument> =
    listOf(
        JournalpostDokument(
            tittel = tittel,
            dokumentvarianter = listOf(
                DokumentVariant.ArkivPDF(Base64.getEncoder().encodeToString(pdf)),
                DokumentVariant.OriginalJson(Base64.getEncoder().encodeToString(originalJson.toByteArray())),
            ),
        ),
    )

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

/**
 * brevkode??
 * Kode som sier noe om dokumentets innhold og oppbygning. Brevkode bør settes for alle journalposttyper, og brukes blant annet for statistikk.
 * For inngående dokumenter kan brevkoden for eksempel være en NAV-skjemaID f.eks. "NAV 14-05.09" eller en SED-id.
 * For utgående dokumenter og notater er det systemet som produserer dokumentet som bestemmer hva brevkoden skal være. Om fagsystemet har "malkoder" kan man gjerne bruke disse som brevkode.
 * vet vi egentlig noe om dette?
 */
data class JournalpostDokument(
    val tittel: String,
    val dokumentvarianter: List<DokumentVariant>,
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
