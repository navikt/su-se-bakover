package no.nav.su.se.bakover

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.DefaultHeaders
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

@KtorExperimentalAPI
fun Application.module(
    suPerson: SuPersonClient,
    suInntekt: SuInntektClient
) {
    install(DefaultHeaders) {
        header("Access-Control-Allow-Origin", fromEnvironment("allowCorsOrigin"))
    }
    val azureTenant = fromEnvironment("azure.tenant")
    setupAuthentication(
        jwksUrl = "https://login.microsoftonline.com/$azureTenant/discovery/v2.0/keys",
        jwtIssuer = "https://login.microsoftonline.com/$azureTenant/v2.0",
        jwtRealm = "su-se-bakover"
    )
    routing {
        get("/isalive") {
            call.respond("ALIVE")
        }
        get("/isready") {
            call.respond("READY")
        }
        authenticate {
            get("/secretest") {
                call.respond("This is the most secret: bla bla")
            }
        }
        get("/hello") {
            val person = suPerson.person()
            val inntekt = suInntekt.inntekt()
            call.respond(
                """
{
    "greeting": "is it me you're looking for? great, cause i.. i.. i... i... I'm staying $person and i have $inntekt in the bank"
}
""".trimIndent()
            )
        }
    }
}

@KtorExperimentalAPI
fun Application.fromEnvironment(path: String): String = environment.config.property(path).getString()

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)