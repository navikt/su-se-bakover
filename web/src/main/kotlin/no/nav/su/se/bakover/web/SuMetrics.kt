import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Metrics.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.micrometer.core.instrument.Metrics as MicrometerMetrics

object SuMetrics {
    private val collectorRegistry = CollectorRegistry(true)
    private val prometheusMeterRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT,
        collectorRegistry,
        Clock.SYSTEM
    )
    enum class Counter(private val value: String) {
        Søknad("soknads_counter");
        fun increment() = MicrometerMetrics.counter(this.value).increment()
    }

    fun setup(): Pair<CollectorRegistry, PrometheusMeterRegistry> {
        addRegistry(prometheusMeterRegistry)
        return Pair(collectorRegistry, prometheusMeterRegistry)
    }
}
