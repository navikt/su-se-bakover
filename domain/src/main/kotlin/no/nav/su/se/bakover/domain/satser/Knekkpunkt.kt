package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.periode.Måned
import java.time.LocalDate

/**
 * Ikrafttredelsesdatoen til en gitt lov/sats.
 * Brukes for å finne ut hvilke satser som gjaldt på en gitt dato.
 * @throws IllegalArgumentException dersom det ikke er den 1. i måneden
 */
@JvmInline
value class Knekkpunkt(
    private val value: LocalDate,
) {
    fun måned(): Måned = Måned.fra(value)

    companion object {
        operator fun LocalDate.compareTo(knekkpunkt: Knekkpunkt): Int {
            return this.compareTo(knekkpunkt.value)
        }
    }
}
