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
import java.util.concurrent.TimeUnit

fun Application.susebakover(env: Environment = Environment()) {
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
            call.respond("is it me you're looking for?")
        }
    }
}

fun main() {
    val app = embeddedServer(Netty, 8080) {
        susebakover()
    }.start(false)

    Runtime.getRuntime().addShutdownHook(Thread {
        app.stop(5, 60, TimeUnit.SECONDS)
    })
}
