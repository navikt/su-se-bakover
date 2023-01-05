package no.nav.su.se.bakover.web.søknadsbehandling

import ch.qos.logback.classic.ClassicConstants
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndDockerDb
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters.firstDayOfYear
import java.time.temporal.TemporalAdjusters.lastDayOfYear

/**
 * Oppretter en ny sak med en ny digital søknad i den lokale postgres-instansen (bruker de samme endepunktene som frontend).
 * Kan kjøres via `./local-db-scripts/create-søknadsbehandling.sh`
 */
fun main() {
    System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback-local.xml")
    withTestApplicationAndDockerDb {
        // Ønsker default en periode som er inneværende år
        val now: LocalDate = LocalDate.now(Clock.systemUTC())
        opprettInnvilgetSøknadsbehandling(
            fraOgMed = now.with(firstDayOfYear()).toString(),
            tilOgMed = now.with(lastDayOfYear()).toString(),
            client = this.client,
        )
    }
}
