package no.nav.su.se.bakover.web.metrics

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tags
import no.nav.su.se.bakover.common.metrics.SuMetrics.incrementCounter
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics.UnderkjentHandlinger
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import java.util.concurrent.atomic.AtomicInteger

class BehandlingMicrometerMetrics : BehandlingMetrics {

    private val behandlingstatuserGauge = BehandlingsStatus.values().associateWith {
        Metrics.gauge("soknadsbehandling_aktive", Tags.of("type", it.name), AtomicInteger(0))
    }

    override fun behandlingsstatusChanged(old: BehandlingsStatus, new: BehandlingsStatus) {
        // Ønsker å kunne vise hvor mange behandlinger vi har i de forskjellige tilstandene. Ved restart kan gaugen bli negativ, men dersom man klarer å summe de vil vi kanskje få lurt det til.
        behandlingstatuserGauge.getValue(new)!!.incrementAndGet()
        behandlingstatuserGauge.getValue(old)!!.decrementAndGet()
        incrementCounter("soknadsbehandling_counter", new.name)
    }

    /* Underkjent behandling er et eget konsept på utsiden av BehandlingStatus. Derfor må den ligge utenfor. */
    override fun incrementUnderkjentCounter(handling: UnderkjentHandlinger) {
        incrementCounter("soknadsbehandling_underkjent", handling.name)
    }

    override fun incrementInnvilgetCounter(handling: BehandlingMetrics.InnvilgetHandlinger) {
        incrementCounter("soknadsbehandling_innvilget", handling.name)
    }

    override fun incrementAvslåttCounter(handling: BehandlingMetrics.AvslåttHandlinger) {
        incrementCounter("soknadsbehandling_avslag", handling.name)
    }

    override fun incrementTilAttesteringCounter(handling: BehandlingMetrics.TilAttesteringHandlinger) {
        incrementCounter("soknadsbehandling_til_attestering", handling.name)
    }
}
