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
import no.nav.su.se.bakover.person.SuPersonClient
import java.util.concurrent.TimeUnit

fun Application.susebakover(env: Environment = Environment(), suPersonClient: SuPersonClient) {
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
            call.respond("is it me you're looking for?, i.. i.. i... i... I'am staying $person")
        }
    }
}

fun main() {
    val app = embeddedServer(Netty, 8080) {
        val suPersonClient = SuPersonClient()
        susebakover(suPersonClient = suPersonClient)
    }.start(false)

    Runtime.getRuntime().addShutdownHook(Thread {
        app.stop(5, 60, TimeUnit.SECONDS)
    })
}
