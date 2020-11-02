package no.nav.su.se.bakover.web.metrics

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Metrics.counter
import io.micrometer.core.instrument.Tags
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import java.util.concurrent.atomic.AtomicInteger

class BehandlingMicrometerMetrics : BehandlingMetrics {

    private val behandlingstatuserGauge = Behandling.BehandlingsStatus.values().map {
        it to Metrics.gauge("soknadsbehandling_aktive", Tags.of("type", it.name), AtomicInteger(0))
    }.toMap()

    override fun behandlingsstatusChanged(old: Behandling.BehandlingsStatus, new: Behandling.BehandlingsStatus) {
        // Ønsker å kunne vise hvor mange behandlinger vi har i de forskjellige tilstandene. Ved restart kan gaugen bli negativ, men dersom man klarer å summe de vil vi kanskje få lurt det til.
        behandlingstatuserGauge.getValue(new)!!.incrementAndGet()
        behandlingstatuserGauge.getValue(old)!!.decrementAndGet()
        incrementCounter("soknadsbehandling_counter", new.name)
    }

    /* Underkjent behandling er et eget konsept på utsiden av BehandlingStatus. Derfor må den ligge utenfor. */
    override fun incrementUnderkjentCounter() = counter("soknadsbehandling_underkjent").increment()

    override fun incrementInnvilgetCounter(handling: BehandlingMetrics.InnvilgetHandlinger) {
        incrementCounter("soknadsbehandling_innvilget", handling.name)
    }

    override fun incrementAvslåttCounter(handling: BehandlingMetrics.AvslåttHandlinger) {
        incrementCounter("soknadsbehandling_avslag", handling.name)
    }

    override fun incrementTilAttesteringCounter(handling: BehandlingMetrics.TilAttesteringHandlinger) {
        incrementCounter("soknadsbehandling_til_attestering", handling.name)
    }

    private fun incrementCounter(metricName: String, type: String) {
        counter(
            metricName,
            Tags.of("type", type)
        ).increment()
    }
}
