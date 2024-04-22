package no.nav.su.se.bakover.bootstrap

import ch.qos.logback.classic.ClassicConstants
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.web.startServer
import org.slf4j.bridge.SLF4JBridgeHandler

/**
 * For å starte lokalt, bruk: no.nav.su.se.bakover.web.LocalApplicationWithTestdata
 */
fun main() {
    if (ApplicationConfig.isRunningLocally()) {
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback-local.xml")
    }
    // https://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
    startServer()
}
