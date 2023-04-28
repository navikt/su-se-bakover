package no.nav.su.se.bakover.application

import ch.qos.logback.classic.ClassicConstants
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.web.startServer

/**
 * For å starte lokalt, bruk: no.nav.su.se.bakover.web.LocalApplicationWithTestdata
 */
fun main() {
    if (ApplicationConfig.isRunningLocally()) {
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback-local.xml")
    }
    startServer()
}
