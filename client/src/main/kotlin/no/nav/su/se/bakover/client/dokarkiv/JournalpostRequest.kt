package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.AvsluttSøknadsBehandlingBody
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.VedtakInnhold
import java.util.Base64

sealed class Journalpost {
    val tema: String = "SUP"
    val behandlingstema: String = "ab0268"
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
        val sakId: String,
        val søknadInnhold: SøknadInnhold,
        val pdf: ByteArray,
    ) : Journalpost() {
        override val tittel: String = "Søknad om supplerende stønad for uføre flyktninger"
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person)
        )
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val sak: Fagsak = Fagsak(sakId)
        override val journalpostType: JournalPostType = JournalPostType.INNGAAENDE
        override val kanal: String? = "INNSENDT_NAV_ANSATT"
        override val journalfoerendeEnhet: String = "9999"
        override val dokumenter: List<JournalpostDokument> = søknadInnhold.toJournalpostDokument()

        private fun SøknadInnhold.toJournalpostDokument() = listOf(
            JournalpostDokument(
                tittel = "Søknad om supplerende stønad for uføre flyktninger",
                dokumentKategori = DokumentKategori.SOK,
                dokumentvarianter = listOf(
                    DokumentVariant(
                        filtype = "PDFA",
                        fysiskDokument = Base64.getEncoder().encodeToString(pdf),
                        variantformat = "ARKIV"
                    ),
                    DokumentVariant(
                        filtype = "JSON",
                        fysiskDokument = Base64.getEncoder()
                            .encodeToString(objectMapper.writeValueAsString(this).toByteArray()),
                        variantformat = "ORIGINAL"
                    )
                )
            )
        )
    }

    data class Vedtakspost(
        val person: Person,
        val sakId: String,
        val vedtakInnhold: VedtakInnhold,
        val pdf: ByteArray,
    ) : Journalpost() {
        override val tittel: String = "Vedtaksbrev for soknad om supplerende stønad"
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person)
        )
        override val sak: Fagsak = Fagsak(sakId)
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val journalpostType: JournalPostType = JournalPostType.UTGAAENDE
        override val kanal: String? = null
        override val journalfoerendeEnhet: String = "4815"
        override val dokumenter: List<JournalpostDokument> = vedtakInnhold.toJournalpostDokument()

        private fun VedtakInnhold.toJournalpostDokument() = listOf(
            JournalpostDokument(
                tittel = "Vedtaksbrev for soknad om supplerende stønad",
                dokumentKategori = DokumentKategori.VB,
                dokumentvarianter = listOf(
                    DokumentVariant(
                        filtype = "PDFA",
                        fysiskDokument = Base64.getEncoder().encodeToString(pdf),
                        variantformat = "ARKIV"
                    ),
                    DokumentVariant(
                        filtype = "JSON",
                        fysiskDokument = Base64.getEncoder()
                            .encodeToString(objectMapper.writeValueAsString(this).toByteArray()),
                        variantformat = "ORIGINAL"
                    )
                )
            )
        )
    }

    data class AvsluttetSøknadsBehandlingPost(
        val person: Person,
        val pdf: ByteArray,
        val avsluttSøknadsBehandlingBody: AvsluttSøknadsBehandlingBody
    ) : Journalpost() {
        override val tittel: String = "Vedtak om avsluttet søknad om supplerende stønad"
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person)
        )
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())

        override val sak: Fagsak = Fagsak(avsluttSøknadsBehandlingBody.sakId.toString())

        override val journalpostType: JournalPostType = JournalPostType.UTGAAENDE
        override val kanal: String? = null
        override val journalfoerendeEnhet: String = "4815"
        override val dokumenter: List<JournalpostDokument> = toJournalpostDokument()

        private fun toJournalpostDokument() = listOf(
            JournalpostDokument(
                tittel = "Vedtak om avsluttet søknad om supplerende stønad",
                dokumentKategori = DokumentKategori.VB,
                dokumentvarianter = listOf(
                    DokumentVariant(
                        filtype = "PDFA",
                        fysiskDokument = Base64.getEncoder().encodeToString(pdf),
                        variantformat = "ARKIV"
                    ),
                    DokumentVariant(
                        filtype = "JSON",
                        fysiskDokument = Base64.getEncoder()
                            .encodeToString(objectMapper.writeValueAsString(this).toByteArray()),
                        variantformat = "ORIGINAL"
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

data class DokumentVariant(
    val filtype: String,
    val fysiskDokument: String,
    val variantformat: String
)

enum class JournalPostType(val type: String) {
    INNGAAENDE("INNGAAENDE"),
    UTGAAENDE("UTGAAENDE")
}

enum class DokumentKategori(val type: String) {
    SOK("SOK"),
    VB("VB")
}
