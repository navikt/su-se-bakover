package no.nav.su.se.bakover.web.søknad.ny

import ch.qos.logback.classic.util.ContextInitializer
import no.nav.su.se.bakover.database.DatabaseBuilder.migrateDatabase
import no.nav.su.se.bakover.database.DatabaseBuilder.newLocalDataSource

/**
 * Oppretter en ny sak med en ny digital søknad i den lokale postgres-instansen (bruker de samme endepunktene som frontend).
 * Kan kjøres via ./resetdb_and_create_søknad.sh
 */
fun main() {
    System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, "logback-local.xml")
    val dataSource = newLocalDataSource()
    migrateDatabase(dataSource)
    nyDigitalSøknad(
        dataSource = dataSource,
    )
}
