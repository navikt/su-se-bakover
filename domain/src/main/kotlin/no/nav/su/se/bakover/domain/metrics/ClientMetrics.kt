package no.nav.su.se.bakover.domain.metrics

import no.nav.su.se.bakover.domain.journalpost.JournalpostClientMetrics

data class ClientMetrics(
    val journalpostClientMetrics: JournalpostClientMetrics,
)
