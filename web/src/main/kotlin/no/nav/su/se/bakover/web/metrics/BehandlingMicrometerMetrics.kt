package no.nav.su.se.bakover.web.metrics

import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics.incrementCounter
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics.UnderkjentHandlinger

class BehandlingMicrometerMetrics : BehandlingMetrics {

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
