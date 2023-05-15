package no.nav.su.se.bakover.web

import ch.qos.logback.classic.ClassicConstants
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.su.se.bakover.common.ApplicationConfig

fun main() {
    if (ApplicationConfig.isRunningLocally()) {
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback-local.xml")
    }
    embeddedServer(factory = Netty, port = 8080, module = {
        susebakover(extraRoutes = { this.testDataRoutes() })
    }).start(true)
}
