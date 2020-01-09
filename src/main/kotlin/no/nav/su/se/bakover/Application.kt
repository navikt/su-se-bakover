package no.nav.su.se.bakover

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
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

@KtorExperimentalAPI
fun Application.module(
        suPerson: SuPersonClient,
        suInntekt: SuInntektClient
) {
    install(CallLogging) {
        //default level TRACE
    }

    install(CORS) {
        method(Options)
        header(Authorization)
        host(fromEnvironment("cors.allow.origin"), listOf("http","https"))
    }

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
                call.respond(suPerson.person())
            }
        }
    }
}

@KtorExperimentalAPI
fun Application.fromEnvironment(path: String): String = environment.config.property(path).getString()

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)