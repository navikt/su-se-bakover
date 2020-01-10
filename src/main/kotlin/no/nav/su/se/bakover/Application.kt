package no.nav.su.se.bakover

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.response.respondTextWriter
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.inntekt.SuInntektClient
import no.nav.su.se.bakover.person.SuPersonClient

@KtorExperimentalAPI
fun Application.susebakover() {
    module(
            SuPersonClient(fromEnvironment("integrations.suPerson.url")),
            SuInntektClient(fromEnvironment("integrations.suInntekt.url"))
    )
}

const val personPath = "/person"
const val identLabel = "ident"

@KtorExperimentalAPI
fun Application.module(
        suPerson: SuPersonClient,
        suInntekt: SuInntektClient
) {
    val collectorRegistry = CollectorRegistry.defaultRegistry

    install(CallLogging) {
        //default level TRACE
    }

    install(CORS) {
        method(Options)
        header(Authorization)
        host(fromEnvironment("cors.allow.origin"), listOf("http", "https"))
    }

    install(MicrometerMetrics) {
        registry = PrometheusMeterRegistry(
                PrometheusConfig.DEFAULT,
                collectorRegistry,
                Clock.SYSTEM
        )
        meterBinders = listOf(
                ClassLoaderMetrics(),
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                ProcessorMetrics(),
                JvmThreadMetrics(),
                LogbackMetrics()
        )
    }

    val azureClient = AzureClient(
        fromEnvironment("azure.clientId"),
        fromEnvironment("azure.clientSecret"),
        fromEnvironment("azure.wellknownUrl")
    )

    setupAuthentication(
            wellKnownUrl = fromEnvironment("azure.wellknownUrl"),
            requiredGroup = fromEnvironment("azure.requiredGroup"),
            clientId = fromEnvironment("azure.clientId"),
            clientSecret = fromEnvironment("azure.clientSecret"),
            tenant = fromEnvironment("azure.tenant"),
            backendCallbackUrl = fromEnvironment("azure.backendCallbackUrl")
    )
    oauthRoutes(
            frontendRedirectUrl = fromEnvironment("integrations.suSeFramover.redirectUrl")
    )
    routing {
        get("/isalive") {
            call.respond("ALIVE")
        }

        get("/isready") {
            call.respond("READY")
        }

        get("/metrics") {
            val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: emptySet()
            call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
            }
        }

        authenticate("jwt") {
            get("/authenticated") {
                var principal = (call.authentication.principal as JWTPrincipal).payload
                call.respond("""
                    {
                        "data": "Congrats ${principal.getClaim("name").asString()}, you are successfully authenticated with a JWT token"
                    }
                """.trimIndent())
            }
            get(personPath) {
                    val suPersonToken = azureClient.exchangeToken(call.request.header(Authorization)!!)
                    call.respond(suPerson.person(ident = call.parameters[identLabel]!!, suPersonToken = suPersonToken))
            }
        }
    }
}

@KtorExperimentalAPI
fun Application.fromEnvironment(path: String): String = environment.config.property(path).getString()

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)