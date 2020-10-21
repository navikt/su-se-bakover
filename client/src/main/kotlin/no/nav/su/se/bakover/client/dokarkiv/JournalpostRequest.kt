package no.nav.su.se.bakover.client.dokarkiv

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.VedtakInnhold
import java.util.Base64
import java.util.UUID

internal const val ENHET_ÅLESUND = "4815"

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
        override val dokumenter: List<JournalpostDokument> = listOf(
            JournalpostDokument(
                tittel = tittel,
                dokumentKategori = DokumentKategori.SOK,
                dokumentvarianter = listOf(
                    DokumentVariant.Arkiv(
                        fysiskDokument = Base64.getEncoder().encodeToString(pdf)
                    ),
                    DokumentVariant.OriginalJson(
                        fysiskDokument =
                            Base64.getEncoder().encodeToString(objectMapper.writeValueAsString(søknadInnhold).toByteArray())
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
        override val journalfoerendeEnhet: String = ENHET_ÅLESUND
        override val dokumenter: List<JournalpostDokument> = listOf(
            JournalpostDokument(
                tittel = tittel,
                dokumentKategori = DokumentKategori.VB,
                dokumentvarianter = listOf(
                    DokumentVariant.Arkiv(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                    DokumentVariant.OriginalJson(
                        fysiskDokument =
                            Base64.getEncoder().encodeToString(objectMapper.writeValueAsString(vedtakInnhold).toByteArray())
                    )
                )
            )
        )
    }

    data class LukketSøknadJournalpostRequest(
        val person: Person,
        val pdf: ByteArray,
        val sakId: UUID,
        val lukketSøknadBrevinnhold: LukketSøknadBrevinnhold,
    ) : Journalpost() {
        override val tittel: String = when (lukketSøknadBrevinnhold) {
            is LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold -> "Bekrefter at søknad er trukket"
            else -> throw java.lang.RuntimeException(
                "template kan bare være trukket"
            )
        }
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(
            id = person.ident.fnr.toString(),
            navn = søkersNavn(person)
        )
        override val sak: Fagsak = Fagsak(sakId.toString())
        override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
        override val journalpostType: JournalPostType = JournalPostType.UTGAAENDE
        override val kanal: String? = null
        override val journalfoerendeEnhet: String = ENHET_ÅLESUND
        override val dokumenter: List<JournalpostDokument> = listOf(
            JournalpostDokument(
                tittel = tittel,
                dokumentKategori = DokumentKategori.Infobrev,
                dokumentvarianter = listOf(
                    DokumentVariant.Arkiv(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                    DokumentVariant.OriginalJson(
                        fysiskDokument =
                            Base64.getEncoder().encodeToString(objectMapper.writeValueAsString(lukketSøknadBrevinnhold).toByteArray())
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
    data class Arkiv(
        val filtype: String = "PDFA",
        val fysiskDokument: String,
        val variantformat: String = "ARKIV"
    ) : DokumentVariant()

    data class OriginalJson(
        val filtype: String = "JSON",
        val fysiskDokument: String,
        val variantformat: String = "ORIGINAL"
    ) : DokumentVariant()
}

enum class JournalPostType(val type: String) {
    INNGAAENDE("INNGAAENDE"),
    UTGAAENDE("UTGAAENDE")
}

enum class DokumentKategori(@JsonValue val type: String) {
    SOK("SOK"),
    VB("VB"),
    Infobrev("IB")
}
