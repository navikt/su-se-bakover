package no.nav.su.se.bakover.web.søknadsbehandling

import ch.qos.logback.classic.ClassicConstants
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndDockerDb

/**
 * Oppretter en ny sak med en ny digital søknad i den lokale postgres-instansen (bruker de samme endepunktene som frontend).
 * Kan kjøres via `./local-db-scripts/create-søknadsbehandling.sh`
 */
fun main() {
    System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback-local.xml")
    withTestApplicationAndDockerDb {
        opprettInnvilgetSøknadsbehandling()
    }
}
