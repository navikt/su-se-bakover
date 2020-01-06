package no.nav.su.se.bakover

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwk.UrlJwkProvider
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.features.DefaultHeaders
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.su.se.bakover.inntekt.SuInntektClient
import no.nav.su.se.bakover.person.SuPersonClient
import java.net.URL
import java.util.concurrent.TimeUnit

fun Application.susebakover(
    env: Environment = Environment(),
    suPersonClient: SuPersonClient,
    suInntektClient: SuInntektClient
) {
    install(DefaultHeaders) {
        header("Access-Control-Allow-Origin", env.allowCorsOrigin)
    }
    val jwkProvider = JwkProviderBuilder(URL(env.jwksUrl))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
    install(Authentication) {
        jwt {
            realm = "supstonad"
            verifier(UrlJwkProvider(URL(env.jwksUrl)), env.jwtIssuer)
            validate { credential ->
                val validAudience = true
                val validSubject = true

                if (validAudience && validSubject) {
                    JWTPrincipal(credential.payload)
                } else {
                    if (!validAudience) log.info("Invalid audience: ${credential.payload.audience}")
                    if (!validSubject) log.info("Invalid subject: ${credential.payload.subject}")
                    null
                }
            }
        }
    }
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
            val person = suPersonClient.person()
            val inntekt = suInntektClient.inntekt()
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

fun main() {
    val app = embeddedServer(Netty, 8080) {
        val env = Environment()
        val suPersonClient = SuPersonClient(env)
        val suInntektClient = SuInntektClient(env)
        susebakover(suPersonClient = suPersonClient, suInntektClient = suInntektClient)
    }.start(false)

    Runtime.getRuntime().addShutdownHook(Thread {
        app.stop(5, 60, TimeUnit.SECONDS)
    })
}
