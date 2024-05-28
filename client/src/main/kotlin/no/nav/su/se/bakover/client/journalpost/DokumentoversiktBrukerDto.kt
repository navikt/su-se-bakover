package no.nav.su.se.bakover.client.journalpost

internal data class HentJournalposterForBruker(
    val brukerId: BrukerId,
    val fraDato: String? = null,
    val tilDato: String? = null,
    val tema: List<String> = listOf("SUP"),
    val journalposttyper: List<String> = emptyList(),
    val journalstatuser: List<String> = emptyList(),
    val foerste: Int = 50,
)

internal data class BrukerId(
    val id: String,
    val type: String = "FNR",
)

internal data class HentDokumentoversiktBrukerHttpResponse(
    override val data: HentDokumentoversiktBrukerResponse?,
    override val errors: List<Error>?,
) : GraphQLHttpResponse()

internal data class HentDokumentoversiktBrukerResponse(
    val dokumentoversiktBruker: DokumentoversiktBruker,
)

internal data class DokumentoversiktBruker(
    val journalposter: List<Journalpost>,
)
