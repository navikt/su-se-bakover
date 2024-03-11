package no.nav.su.se.bakover.web.metrics

import dokument.domain.journalføring.JournalpostClientMetrics
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics.incrementCounter

class JournalpostClientMicrometerMetrics : JournalpostClientMetrics {

    override fun inkrementerBenyttetSkjema(skjema: JournalpostClientMetrics.BenyttetSkjema) {
        incrementCounter("supstonad_benyttet_skjema_kontrollsamtale", skjema.name)
    }
}
