package no.nav.su.se.bakover.client.dokarkiv

internal data class JournalpostRequest(
    val tittel: String = "Søknad om supplerende stønad for uføre flyktninger",
    val journalpostType: DokArkivClient.JournalPostType,
    val tema: String = "SUP",
    val kanal: String? = null,
    val behandlingstema: String = "ab0268",
    val journalfoerendeEnhet: String = "9999",
    val avsenderMottaker: AvsenderMottaker,
    val bruker: Bruker,
    val sak: Sak,
    val dokumenter: List<JournalpostDokument>
)

internal data class AvsenderMottaker(
    val id: String,
    val idType: String = "FNR",
    val navn: String
)
internal data class Bruker(
    val id: String,
    val idType: String = "FNR"
)
internal data class Sak(
    val fagsakId: String,
    val fagsaksystem: String = "SUPSTONAD",
    val sakstype: String = "FAGSAK"
)

internal data class JournalpostDokument(
    val tittel: String = "Søknad om supplerende stønad for uføre flyktninger",
    val dokumentKategori: DokArkivClient.DokumentKategori,
    val brevkode: String = "XX.YY-ZZ",
    val dokumentvarianter: List<DokumentVariant>
)

internal data class DokumentVariant(
    val filtype: String,
    val fysiskDokument: String,
    val variantformat: String
)
