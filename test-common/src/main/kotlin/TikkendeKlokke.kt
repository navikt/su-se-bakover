package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.startOfDay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Klokke som tikker med 1 sekund for hver gang [instant] kalles.
 * Hjelpemiddel for å generere testdata som er temporalt avhengig av hverandre. Typisk vil dette være objekter
 * som kan plasseres på en tidslinje (f.eks vedtak, utbetalinger og grunnlagsdata)
 */
class TikkendeKlokke(
    private val initialClock: Clock = fixedClock,
) : Clock() {
    private var nextInstant = initialClock.instant()

    override fun getZone(): ZoneId = initialClock.zone

    override fun withZone(zone: ZoneId?): Clock = initialClock.withZone(zone)

    override fun instant(): Instant {
        nextInstant = nextInstant.plus(1, ChronoUnit.SECONDS)
        return nextInstant
    }

    fun spolTil(dato: LocalDate): Instant {
        require(dato.startOfDay(zone) > nextInstant) { "Kan bare spole fremover i tid" }
        do {
            instant()
        } while (nextInstant < dato.startOfDay(zone).instant)
        return nextInstant
    }

    fun copy(): TikkendeKlokke = TikkendeKlokke(initialClock)
}
