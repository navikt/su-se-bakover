package no.nav.su.se.bakover.web.metrics

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Histogram

object SuMetrics {
    private val collectorRegistry = CollectorRegistry(true)
    private val prometheusMeterRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT,
        collectorRegistry,
        Clock.SYSTEM
    )
    val dbTimer = Histogram.build("db_query_latency_histogram", "Histogram av eksekveringstid for db-spørringer")
        .labelNames("query")
        .register(collectorRegistry)

    fun setup(): Pair<CollectorRegistry, PrometheusMeterRegistry> {
        Metrics.addRegistry(prometheusMeterRegistry)
        return Pair(collectorRegistry, prometheusMeterRegistry)
    }

    internal fun incrementCounter(metricName: String, type: String) {
        Metrics.counter(
            metricName,
            Tags.of("type", type)
        ).increment()
    }
}
