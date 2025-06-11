package no.nav.su.statistikk

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
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
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

fun main() {
    embeddedServer(
        factory = Netty,
        port = 8090,
        module = {
            val log = LoggerFactory.getLogger("no.nav.su.statistikk.Application")

            installMetrics(PrometheusMeterRegistry(PrometheusConfig.DEFAULT))
            naisRoutes(PrometheusMeterRegistry(PrometheusConfig.DEFAULT))

            setupKtorCallId()
            setupKtorCallLogging()

            routing {
                // api()
            }

            log.info("SU Statistikk kjører!")
        },
    ).start(true)
}
/*
private fun Route.api() {
    // authenticate {}
}
*/

// TODO egen fil?

const val CORRELATION_ID_HEADER = "X-Correlation-ID"

private fun Application.setupKtorCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            if (call.request.httpMethod.value == "OPTIONS") return@filter false
            if (call.pathShouldBeExcluded(naisPaths)) return@filter false

            return@filter true
        }
        callIdMdc(CORRELATION_ID_HEADER)

        mdc("Authorization") { it.authHeader() }
        disableDefaultColors()
    }
}

fun ApplicationCall.authHeader(): String {
    return this.request.header(HttpHeaders.Authorization).toString()
}

private fun ApplicationCall.pathShouldBeExcluded(paths: List<String>): Boolean {
    return paths.any {
        this.request.path().startsWith(it)
    }
}

private fun Application.setupKtorCallId() {
    install(CallId) {
        header(XCorrelationId)
        this.generate(length = 17)
        verify { it.isNotEmpty() }
    }
}

// TODO egen fil
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
