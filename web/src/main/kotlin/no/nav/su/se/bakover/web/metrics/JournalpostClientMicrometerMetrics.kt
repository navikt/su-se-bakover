package no.nav.su.se.bakover.web.metrics

import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics.incrementCounter
import no.nav.su.se.bakover.domain.journalpost.JournalpostClientMetrics

class JournalpostClientMicrometerMetrics : JournalpostClientMetrics {

    override fun inkrementerBenyttetSkjema(skjema: JournalpostClientMetrics.BenyttetSkjema) {
        incrementCounter("supstonad_benyttet_skjema_kontrollsamtale", skjema.name)
    }
}
