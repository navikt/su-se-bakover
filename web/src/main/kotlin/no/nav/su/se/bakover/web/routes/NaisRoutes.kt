package no.nav.su.se.bakover.web.routes

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.ContentType
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.response.respond
import io.ktor.response.respondTextWriter
import io.ktor.routing.get
import io.ktor.routing.routing
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat

private const val IS_ALIVE_PATH = "/isalive"
private const val IS_READY_PATH = "/isready"
private const val METRICS_PATH = "/metrics"

internal val naisPaths = listOf(IS_ALIVE_PATH, IS_READY_PATH, METRICS_PATH)

internal fun Application.naisRoutes(collectorRegistry: CollectorRegistry) {
    routing {
        get(IS_ALIVE_PATH) {
            call.respond("ALIVE")
        }

        get(IS_READY_PATH) {
            call.respond("READY")
        }

        get(METRICS_PATH) {
            val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: emptySet()
            call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
            }
        }
    }
}

internal fun Application.installMetrics(prometheusMeterRegistry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        registry = prometheusMeterRegistry
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            LogbackMetrics()
        )
    }
}
