package no.nav.su.se.bakover.client.saf

internal data class SafResponse(
    val data: Data,
)

internal data class Data(
    val journalpost: Journalpost,
)

internal data class Journalpost(
    val tema: String,
    val sak: Sak,
)

internal data class Sak(
    val fagsakId: String,
    val fagsaksystem: String,
    val sakstype: String,
    val tema: String,
    val datoOpprettet: String,
)
