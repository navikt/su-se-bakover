package no.nav.su.se.bakover.common.periode

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Supertype for [Periode] som tillater at datoer ikke faller på første/siste dag i en måned.
 * Logikk som ikke er spesifikk for [Periode] kan flyttes hit.
 */
open class DatoIntervall(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
) {
    infix fun inneholder(dato: LocalDate): Boolean = dato in fraOgMed..tilOgMed

    /** Inkluderer første og siste dag. */
    fun antallDager(): Long = ChronoUnit.DAYS.between(fraOgMed, tilOgMed.plusDays(1))
}
