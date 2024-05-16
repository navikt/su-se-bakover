package no.nav.su.se.bakover.web.routes

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

private const val IS_ALIVE_PATH = "/isalive"
private const val IS_READY_PATH = "/isready"
private const val METRICS_PATH = "/metrics"

internal val naisPaths = listOf(IS_ALIVE_PATH, IS_READY_PATH, METRICS_PATH)

internal fun Application.naisRoutes(prometheusMeterRegistry: PrometheusMeterRegistry) {
    routing {
        get(IS_ALIVE_PATH) {
            call.respondText("ALIVE")
        }

        get(IS_READY_PATH) {
            call.respondText("READY")
        }

        get(METRICS_PATH) {
            call.respondText(prometheusMeterRegistry.scrape())
        }
    }
}

fun Application.installMetrics(prometheusMeterRegistry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        registry = prometheusMeterRegistry
        meterBinders = listOf(
            UptimeMetrics(),
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            LogbackMetrics(),
        )
    }
}
