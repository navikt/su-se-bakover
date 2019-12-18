package no.nav.su.se.bakover

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.DefaultHeaders
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.su.se.bakover.inntekt.SuInntektClient
import no.nav.su.se.bakover.person.SuPersonClient
import java.util.concurrent.TimeUnit

fun Application.susebakover(env: Environment = Environment(), suPersonClient: SuPersonClient, suInntektClient: SuInntektClient) {
    install(DefaultHeaders) {
        header("Access-Control-Allow-Origin", env.allowCorsOrigin)
    }
    routing {
        get("/isalive") {
            call.respond("ALIVE")
        }
        get("/isready") {
            call.respond("READY")
        }
        get("/hello") {
            val person = suPersonClient.person()
            val inntekt = suInntektClient.inntekt()
            call.respond("is it me you're looking for? great, cause i.. i.. i... i... I'm staying $person and i have $inntekt in the bank")
        }
    }
}

fun main() {
    val app = embeddedServer(Netty, 8080) {
        val suPersonClient = SuPersonClient()
        val suInntektClient = SuInntektClient()
        susebakover(suPersonClient = suPersonClient, suInntektClient = suInntektClient)
    }.start(false)

    Runtime.getRuntime().addShutdownHook(Thread {
        app.stop(5, 60, TimeUnit.SECONDS)
    })
}
