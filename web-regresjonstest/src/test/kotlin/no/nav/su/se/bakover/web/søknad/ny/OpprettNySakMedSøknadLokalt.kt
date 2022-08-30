package no.nav.su.se.bakover.web.søknad.ny

import ch.qos.logback.classic.ClassicConstants
import no.nav.su.se.bakover.web.SharedRegressionTestData

/**
 * Oppretter en ny sak med en ny digital søknad i den lokale postgres-instansen (bruker de samme endepunktene som frontend).
 * Kan kjøres via ./resetdb_and_create_søknad.sh
 */
fun main() {
    System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback-local.xml")
    SharedRegressionTestData.withTestApplicationAndDockerDb {
        nyDigitalSøknad()
    }
}
