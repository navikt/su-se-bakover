package no.nav.su.se.bakover.common.infrastructure.metrics

import com.github.benmanes.caffeine.cache.Cache
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.core.metrics.Histogram

data object SuMetrics {
    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val dbTimer: Histogram = Histogram.builder()
        .name("db_query_latency_histogram")
        .help("Histogram av eksekveringstid for db-spørringer")
        .labelNames("query")
        .unit(io.prometheus.metrics.model.snapshots.Unit.SECONDS)
        .register(prometheusMeterRegistry.prometheusRegistry)

    fun <K, V> monitorCache(cache: Cache<K, V>, cacheName: String) {
        CaffeineCacheMetrics.monitor(prometheusMeterRegistry, cache, cacheName)
    }
}
