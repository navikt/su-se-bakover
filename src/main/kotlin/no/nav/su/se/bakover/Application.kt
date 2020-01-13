package no.nav.su.se.bakover

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.config.ApplicationConfig
import io.ktor.features.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.azure.getJWKConfig
import no.nav.su.se.bakover.inntekt.SuInntektClient
import no.nav.su.se.bakover.person.SuPersonClient
import org.slf4j.MDC
import org.slf4j.event.Level
import java.net.URL

@KtorExperimentalAPI
fun Application.susebakover() {
    module(
            SuPersonClient(fromEnvironment("integrations.suPerson.url")),
            SuInntektClient(fromEnvironment("integrations.suInntekt.url"))
    )
}

const val personPath = "/person"
const val inntektPath = "/inntekt"
const val identLabel = "ident"

@KtorExperimentalAPI
fun Application.module(
        suPerson: SuPersonClient,
        suInntekt: SuInntektClient
) {

    install(CORS) {
        method(Options)
        header(Authorization)
        host(fromEnvironment("cors.allow.origin"), listOf("http", "https"))
    }

    val collectorRegistry = CollectorRegistry.defaultRegistry
    installMetrics(collectorRegistry)
    naisRoutes(collectorRegistry)

    val jwkConfig = getJWKConfig(fromEnvironment("azure.wellknownUrl"))
    val jwkProvider = JwkProviderBuilder(URL(jwkConfig.getString("jwks_uri"))).build()

    val azureClient = AzureClient(
            fromEnvironment("azure.clientId"),
            fromEnvironment("azure.clientSecret"),
            jwkConfig.getString("token_endpoint")
    )

    setupAuthentication(
            jwkConfig = jwkConfig,
            jwkProvider = jwkProvider,
            config = environment.config
    )
    oauthRoutes(
            frontendRedirectUrl = fromEnvironment("integrations.suSeFramover.redirectUrl")
    )
    routing {

        authenticate("jwt") {
            install(CallId) {
                header(XRequestId)
                generate { "invalid" }
                verify { callId: String ->
                    if (callId == "invalid") throw RejectedCallIdException(callId) else true
                }
            }
            install(CallLogging) {
                level = Level.INFO
                intercept(ApplicationCallPipeline.Monitoring) {
                    MDC.put(XRequestId, call.callId)
                }
            }

            get(path = "/authenticated") {
                val principal = (call.authentication.principal as JWTPrincipal).payload
                call.respond("""
                    {
                        "data": "Congrats ${principal.getClaim("name").asString()}, you are successfully authenticated with a JWT token"
                    }
                """.trimIndent())
            }
            get(personPath) {
                val suPersonToken = azureClient.onBehalfOFToken(call.request.header(Authorization)!!, fromEnvironment("integrations.suPerson.clientId"))
                call.respond(suPerson.person(ident = call.parameters[identLabel]!!, suPersonToken = suPersonToken))
            }

            get(path = inntektPath) {
                val suInntektToken = azureClient.onBehalfOFToken(call.request.header(Authorization)!!, fromEnvironment("integrations.suInntekt.clientId"))
                call.respond(suInntekt.inntekt(ident = call.parameters[identLabel]!!, suInntektToken = suInntektToken))
            }
        }
    }
}

@KtorExperimentalAPI
fun Application.fromEnvironment(path: String): String = environment.config.property(path).getString()

@KtorExperimentalAPI
fun ApplicationConfig.getProperty(key: String): String = property(key).getString()

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)