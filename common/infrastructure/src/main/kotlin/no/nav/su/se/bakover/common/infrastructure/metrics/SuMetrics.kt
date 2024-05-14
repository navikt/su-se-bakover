package no.nav.su.se.bakover.common.infrastructure.metrics

import com.github.benmanes.caffeine.cache.Cache
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Histogram

data object SuMetrics {
    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val dbTimer: Histogram =
        Histogram.build("db_query_latency_histogram", "Histogram av eksekveringstid for db-spørringer")
            .labelNames("query")
            .register(prometheusMeterRegistry.prometheusRegistry)

    fun <K, V> monitorCache(cache: Cache<K, V>, cacheName: String) {
        CaffeineCacheMetrics.monitor(prometheusMeterRegistry, cache, cacheName)
    }
}
