package no.nav.su.se.bakover.web

import ch.qos.logback.classic.ClassicConstants
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import org.slf4j.bridge.SLF4JBridgeHandler
import java.lang.IllegalStateException

fun main() {
    if (!ApplicationConfig.isRunningLocally()) {
        throw IllegalStateException("You should not run this main method on nais (preprod/prod. See Application.main() instead")
    }
    System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback-local.xml")
    // https://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
    embeddedServer(factory = Netty, port = 8080, module = {
        susebakover(extraRoutes = { this.testDataRoutes() })
    }).start(true)
}
