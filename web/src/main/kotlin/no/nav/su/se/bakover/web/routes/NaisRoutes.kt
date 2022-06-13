package no.nav.su.se.bakover.web.routes

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
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
            call.respondText("ALIVE")
        }

        get(IS_READY_PATH) {
            call.respondText("READY")
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
            // We don't use LogbackMetrics anymore because of this impossible cast:  this(tags, (LoggerContext) LoggerFactory.getILoggerFactory());
            // See https://github.com/micrometer-metrics/micrometer/issues/2868 for more information
        )
    }
}
