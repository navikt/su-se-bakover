package no.nav.su.se.bakover.common.infrastructure.metrics

import com.github.benmanes.caffeine.cache.Cache
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Histogram

object SuMetrics {
    private val collectorRegistry = CollectorRegistry(true)
    private val prometheusMeterRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT,
        collectorRegistry,
        Clock.SYSTEM,
    )
    val dbTimer: Histogram =
        Histogram.build("db_query_latency_histogram", "Histogram av eksekveringstid for db-spørringer")
            .labelNames("query")
            .register(collectorRegistry)

    fun setup(): Pair<CollectorRegistry, PrometheusMeterRegistry> {
        Metrics.addRegistry(prometheusMeterRegistry)
        return Pair(collectorRegistry, prometheusMeterRegistry)
    }

    fun <K, V> monitorCache(cache: Cache<K, V>, cacheName: String) {
        CaffeineCacheMetrics.monitor(prometheusMeterRegistry, cache, cacheName)
    }

    fun incrementCounter(metricName: String, type: String) {
        Metrics.counter(
            metricName,
            Tags.of("type", type),
        ).increment()
    }

    enum class Metrikk(val navn: String) {
        SØKNAD_MOTTATT("soknad.mottatt"),
        BEHANDLING_STARTET("behandling.startet"),
        VEDTAK_IVERKSATT("vedtak.iverksatt"),
    }

    enum class Søknadstype(val type: String) {
        PAPIR("papir"),
        DIGITAL("digital"),
    }

    enum class Behandlingstype(val type: String) {
        SØKNAD("soknadsbehandling"),
        REVURDERING("revurdering"),
    }

    // TODO metrikkene burde prefikses
    private fun counter(metrikk: Metrikk, type: String): Counter = Metrics.counter(metrikk.navn, Tags.of("type", type))

    private val søknadPapirMottattCounter = counter(Metrikk.SØKNAD_MOTTATT, type = Søknadstype.PAPIR.type)
    private val søknadDigitalMottattCounter = counter(Metrikk.SØKNAD_MOTTATT, type = Søknadstype.DIGITAL.type)
    private val søknadsbehandlingStartetCounter = counter(Metrikk.BEHANDLING_STARTET, type = Behandlingstype.SØKNAD.type)
    private val revurderingStartetCounter = counter(Metrikk.BEHANDLING_STARTET, type = Behandlingstype.REVURDERING.type)
    private val vedtakSøknadsbehandlingIverksattCounter = counter(Metrikk.VEDTAK_IVERKSATT, type = Behandlingstype.SØKNAD.type)
    private val vedtakRevurderingIverksattCounter = counter(Metrikk.VEDTAK_IVERKSATT, type = Behandlingstype.REVURDERING.type)

    fun søknadMottatt(type: Søknadstype) {
        when (type) {
            Søknadstype.DIGITAL -> søknadDigitalMottattCounter
            Søknadstype.PAPIR -> søknadPapirMottattCounter
        }.increment()
    }

    fun behandlingStartet(type: Behandlingstype) {
        when (type) {
            Behandlingstype.SØKNAD -> søknadsbehandlingStartetCounter
            Behandlingstype.REVURDERING -> revurderingStartetCounter
        }.increment()
    }

    fun vedtakIverksatt(type: Behandlingstype) {
        when (type) {
            Behandlingstype.SØKNAD -> vedtakSøknadsbehandlingIverksattCounter
            Behandlingstype.REVURDERING -> vedtakRevurderingIverksattCounter
        }.increment()
    }
}
