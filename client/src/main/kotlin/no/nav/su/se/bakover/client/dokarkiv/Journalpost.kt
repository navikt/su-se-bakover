package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Behandlingstema
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.Tema
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import java.util.Base64

sealed class Journalpost {
    val tema: String = Tema.SUPPLERENDE_STØNAD.value
    val behandlingstema: String = Behandlingstema.SU_UFØRE_FLYKNING.value
    abstract val journalfoerendeEnhet: String
    abstract val tittel: String
    abstract val journalpostType: JournalPostType
    abstract val kanal: String?
    abstract val avsenderMottaker: AvsenderMottaker
    abstract val bruker: Bruker
    abstract val sak: Fagsak
    abstract val dokumenter: List<JournalpostDokument>
    fun søkersNavn(person: Person): String =
        """${person.navn.etternavn}, ${person.navn.fornavn} ${person.navn.mellomnavn ?: ""}""".trimEnd()

    data class Søknadspost(
        val person: Person,
        val saksnummer: Saksnummer,
        val søknadInnhold: SøknadInnhold,
        val pdf: ByteArray,
    ) : Journalpost() {
        override val tittel: String = "Søknad om supplerende stønad for uføre flyktninger"
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person)
        )
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val sak: Fagsak = Fagsak(saksnummer.nummer.toString())
        override val journalpostType: JournalPostType = JournalPostType.INNGAAENDE
        override val kanal: String = "INNSENDT_NAV_ANSATT"
        override val journalfoerendeEnhet: String = "9999"
        override val dokumenter: List<JournalpostDokument> = søknadInnhold.toJournalpostDokument()

        private fun SøknadInnhold.toJournalpostDokument() = listOf(
            JournalpostDokument(
                tittel = "Søknad om supplerende stønad for uføre flyktninger",
                dokumentKategori = DokumentKategori.SOK,
                dokumentvarianter = listOf(
                    DokumentVariant.ArkivPDF(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                    DokumentVariant.OriginalJson(
                        fysiskDokument = Base64.getEncoder()
                            .encodeToString(objectMapper.writeValueAsString(this).toByteArray()),
                    )
                )
            )
        )
    }

    data class Vedtakspost(
        val person: Person,
        val saksnummer: Saksnummer,
        val brevInnhold: BrevInnhold,
        val pdf: ByteArray
    ) : Journalpost() {
        override val tittel = brevInnhold.brevTemplate.tittel()
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person)
        )
        override val sak: Fagsak = Fagsak(saksnummer.nummer.toString())
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val journalpostType: JournalPostType = JournalPostType.UTGAAENDE
        override val kanal: String? = null
        override val journalfoerendeEnhet: String = "4815"
        override val dokumenter: List<JournalpostDokument> = listOf(
            JournalpostDokument(
                tittel = tittel,
                dokumentKategori = DokumentKategori.VB,
                dokumentvarianter = listOf(
                    DokumentVariant.ArkivPDF(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                    DokumentVariant.OriginalJson(
                        fysiskDokument = Base64.getEncoder().encodeToString(brevInnhold.toJson().toByteArray()),
                    )
                )
            )
        )
    }

    data class Info(
        val person: Person,
        val saksnummer: Saksnummer,
        val brevInnhold: BrevInnhold,
        val pdf: ByteArray,
    ) : Journalpost() {
        override val tittel = brevInnhold.brevTemplate.tittel()
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person)
        )
        override val sak: Fagsak = Fagsak(saksnummer.nummer.toString())
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val journalpostType: JournalPostType = JournalPostType.UTGAAENDE
        override val kanal: String? = null
        override val journalfoerendeEnhet: String = "4815"
        override val dokumenter: List<JournalpostDokument> = listOf(
            JournalpostDokument(
                tittel = tittel,
                dokumentKategori = DokumentKategori.IB,
                dokumentvarianter = listOf(
                    DokumentVariant.ArkivPDF(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                    DokumentVariant.OriginalJson(
                        fysiskDokument = Base64.getEncoder().encodeToString(brevInnhold.toJson().toByteArray()),
                    )
                )
            )
        )
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
    val dokumenter: List<JournalpostDokument>
)

data class AvsenderMottaker(
    val id: String,
    val idType: String = "FNR",
    val navn: String
)

data class Bruker(
    val id: String,
    val idType: String = "FNR"
)

data class Fagsak(
    val fagsakId: String,
    val fagsaksystem: String = "SUPSTONAD",
    val sakstype: String = "FAGSAK"
)

data class JournalpostDokument(
    val tittel: String,
    val dokumentKategori: DokumentKategori,
    val brevkode: String = "XX.YY-ZZ",
    val dokumentvarianter: List<DokumentVariant>
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
