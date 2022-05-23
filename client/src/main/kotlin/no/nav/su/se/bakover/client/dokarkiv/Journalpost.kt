package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Behandlingstema
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.Søknadstype
import no.nav.su.se.bakover.domain.Tema
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.dokument.Dokument
import java.util.Base64

sealed class Journalpost {
    val tema: String = Tema.SUPPLERENDE_STØNAD.value
    abstract val søknadstype: Søknadstype
    abstract val journalfoerendeEnhet: String
    abstract val tittel: String
    abstract val journalpostType: JournalPostType
    abstract val kanal: String?
    abstract val avsenderMottaker: AvsenderMottaker
    abstract val bruker: Bruker
    abstract val sak: Fagsak
    abstract val dokumenter: List<JournalpostDokument>

    val behandlingstema: String
        get() = when (søknadstype) {
            Søknadstype.ALDER -> Behandlingstema.SU_ALDER.value
            Søknadstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING.value
        }

    fun søkersNavn(person: Person): String =
        """${person.navn.etternavn}, ${person.navn.fornavn} ${person.navn.mellomnavn ?: ""}""".trimEnd()

    data class Søknadspost private constructor(
        val person: Person,
        val saksnummer: Saksnummer,
        override val søknadstype: Søknadstype,
        override val dokumenter: List<JournalpostDokument>,
    ) : Journalpost() {
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person),
        )
        override val tittel: String = lagTittel(søknadstype)
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val sak: Fagsak = Fagsak(saksnummer.nummer.toString())
        override val journalpostType: JournalPostType = JournalPostType.INNGAAENDE
        override val kanal: String = "INNSENDT_NAV_ANSATT"
        override val journalfoerendeEnhet: String = "9999"

        companion object {
            fun lagTittel(søknadstype: Søknadstype) = when (søknadstype) {
                Søknadstype.ALDER -> "Søknad om supplerende stønad for alder"
                Søknadstype.UFØRE -> "Søknad om supplerende stønad for uføre flyktninger"
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
                søknadstype = søknadInnhold.type()
            )

            private fun lagDokumenter(pdf: ByteArray, søknadInnhold: SøknadInnhold): List<JournalpostDokument> =
                listOf(
                    JournalpostDokument(
                        tittel = lagTittel(søknadInnhold.type()),
                        dokumentKategori = DokumentKategori.SOK,
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
        val person: Person,
        val saksnummer: Saksnummer,
        override val dokumenter: List<JournalpostDokument>,
        override val tittel: String,
        override val søknadstype: Søknadstype,
    ) : Journalpost() {
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person),
        )
        override val sak: Fagsak = Fagsak(saksnummer.nummer.toString())
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val journalpostType: JournalPostType = JournalPostType.UTGAAENDE
        override val kanal: String? = null
        override val journalfoerendeEnhet: String = "4815"

        companion object {
            fun from(
                person: Person,
                saksnummer: Saksnummer,
                brevInnhold: BrevInnhold,
                pdf: ByteArray,
                søknadstype: Søknadstype,
            ) = Vedtakspost(
                person = person,
                saksnummer = saksnummer,
                dokumenter = lagDokumenter(
                    tittel = brevInnhold.brevTemplate.tittel(),
                    pdf = pdf,
                    originalJson = brevInnhold.toJson(),
                ),
                tittel = brevInnhold.brevTemplate.tittel(),
                søknadstype = søknadstype
            )

            fun from(
                person: Person,
                saksnummer: Saksnummer,
                dokument: Dokument,
                søknadstype: Søknadstype,
            ) = Vedtakspost(
                person = person,
                saksnummer = saksnummer,
                dokumenter = lagDokumenter(
                    tittel = dokument.tittel,
                    pdf = dokument.generertDokument,
                    originalJson = dokument.generertDokumentJson,
                ),
                tittel = dokument.tittel,
                søknadstype = søknadstype
            )

            private fun lagDokumenter(tittel: String, pdf: ByteArray, originalJson: String): List<JournalpostDokument> =
                listOf(
                    JournalpostDokument(
                        tittel = tittel,
                        dokumentKategori = DokumentKategori.VB,
                        dokumentvarianter = listOf(
                            DokumentVariant.ArkivPDF(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                            DokumentVariant.OriginalJson(
                                fysiskDokument = Base64.getEncoder().encodeToString(originalJson.toByteArray()),
                            ),
                        ),
                    ),
                )
        }
    }

    data class Info private constructor(
        val person: Person,
        val saksnummer: Saksnummer,
        override val dokumenter: List<JournalpostDokument>,
        override val tittel: String,
        override val søknadstype: Søknadstype
    ) : Journalpost() {
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person),
        )
        override val sak: Fagsak = Fagsak(saksnummer.nummer.toString())
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val journalpostType: JournalPostType = JournalPostType.UTGAAENDE
        override val kanal: String? = null
        override val journalfoerendeEnhet: String = "4815"

        companion object {
            fun from(
                person: Person,
                saksnummer: Saksnummer,
                brevInnhold: BrevInnhold,
                pdf: ByteArray,
                søknadstype: Søknadstype,
            ) = Info(
                person = person,
                saksnummer = saksnummer,
                dokumenter = lagDokumenter(
                    tittel = brevInnhold.brevTemplate.tittel(),
                    pdf = pdf,
                    originalJson = brevInnhold.toJson(),
                ),
                tittel = brevInnhold.brevTemplate.tittel(),
                søknadstype = søknadstype
            )

            fun from(
                person: Person,
                saksnummer: Saksnummer,
                dokument: Dokument,
                søknadstype: Søknadstype
            ) = Info(
                person = person,
                saksnummer = saksnummer,
                dokumenter = lagDokumenter(
                    tittel = dokument.tittel,
                    pdf = dokument.generertDokument,
                    originalJson = dokument.generertDokumentJson,
                ),
                tittel = dokument.tittel,
                søknadstype = søknadstype,
            )

            private fun lagDokumenter(tittel: String, pdf: ByteArray, originalJson: String): List<JournalpostDokument> =
                listOf(
                    JournalpostDokument(
                        tittel = tittel,
                        dokumentKategori = DokumentKategori.IB,
                        dokumentvarianter = listOf(
                            DokumentVariant.ArkivPDF(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                            DokumentVariant.OriginalJson(
                                fysiskDokument = Base64.getEncoder().encodeToString(originalJson.toByteArray()),
                            ),
                        ),
                    ),
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
    val avsenderMottaker: AvsenderMottaker,
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
    val dokumentKategori: DokumentKategori,
    val brevkode: String = "XX.YY-ZZ",
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
    INNGAAENDE("INNGAAENDE"),
    UTGAAENDE("UTGAAENDE")
}

enum class DokumentKategori(val type: String) {
    SOK("SOK"),
    VB("VB"),
    IB("IB")
}
