package no.nav.su.se.bakover.web.metrics

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Metrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry

object SuMetrics {
    private val collectorRegistry = CollectorRegistry(true)
    private val prometheusMeterRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT,
        collectorRegistry,
        Clock.SYSTEM
    )

    fun setup(): Pair<CollectorRegistry, PrometheusMeterRegistry> {
        Metrics.addRegistry(prometheusMeterRegistry)
        return Pair(collectorRegistry, prometheusMeterRegistry)
    }
}
