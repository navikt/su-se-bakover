package no.nav.su.se.bakover.client.journalpost

import java.time.LocalDate

/**
 * Generell modell for representasjon av en journalpost i APIet.
 * Merk at alle felter er nullable da vi selv styrer hvilke data vi vil ha i retur vha. graphql
 */
internal data class Journalpost(
    val journalpostId: String? = null,
    val tittel: String? = null,
    val journalposttype: String? = null,
    val journalstatus: String? = null,
    val tema: String? = null,
    val sak: Sak? = null,
    val datoOpprettet: LocalDate? = null,
)

/**
 * https://confluence.adeo.no/display/BOA/Type%3A+Sak
 */
internal data class Sak(
    val fagsakId: String? = null,
)
