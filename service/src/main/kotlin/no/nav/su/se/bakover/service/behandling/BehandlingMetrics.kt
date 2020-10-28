package no.nav.su.se.bakover.service.behandling

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics

object BehandlingMetrics {

    object Innvilget {
        private const val metricName = "førstegangsbehandling_innvilget"

        internal fun incrementInnvilgetBehandlingPersistedCounter(): Unit = incrementCounter(metricName, "PERSISTERT")
        internal fun incrementInnvilgetBehandlingOppgaveCounter(): Unit = incrementCounter(metricName, "OPPGAVE")
        internal fun incrementInnvilgetBehandlingJournalførtCounter(): Unit =
            incrementCounter(metricName, "JOURNALFØRT")

        internal fun incrementInnvilgetBehandlingDistribuertBrevCounter(): Unit =
            incrementCounter(metricName, "DISTRIBUERT_BREV")
    }

    object Avslått {
        private const val metricName = "førstegangsbehandling_avslag"

        internal fun incrementAvslåttBehandlingPersistedCounter(): Unit = incrementCounter(metricName, "PERSISTERT")

        internal fun incrementAvslåttBehandlingJournalførtCounter(): Unit = incrementCounter(metricName, "JOURNALFØRT")

        internal fun incrementAvslåttBehandlingDistribuertBrevCounter(): Unit =
            incrementCounter(metricName, "DISTRIBUERT_BREV")
    }

    object TilAttestering {
        private const val metricName = "førstegangsbehandling_til_attestering"

        internal fun incrementPersistedBehandlingTilAttesteringCounter(): Unit = incrementCounter(metricName, "PERSISTERT")

        internal fun incrementOppgaveForBehandlingTilAttesteringCounter(): Unit = incrementCounter(metricName, "OPPGAVE")
    }

    private fun incrementCounter(metricName: String, type: String) {
        Counter.builder(metricName)
            .tag("type", type)
            .register(Metrics.globalRegistry)
            .increment()
    }
}
