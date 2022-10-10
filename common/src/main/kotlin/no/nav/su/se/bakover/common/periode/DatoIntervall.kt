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

    infix fun inneholder(other: DatoIntervall): Boolean =
        starterSamtidigEllerTidligere(other) && slutterSamtidigEllerSenere(other)

    infix fun starterEtter(dato: LocalDate): Boolean = tilOgMed.isAfter(dato)

    infix fun starterSamtidigEllerTidligere(other: DatoIntervall) = starterSamtidig(other) || starterTidligere(other)
    infix fun starterSamtidigEllerSenere(other: DatoIntervall) = starterSamtidig(other) || starterEtter(other)
    infix fun starterSamtidig(other: DatoIntervall) = fraOgMed.isEqual(other.fraOgMed)
    infix fun starterTidligere(other: DatoIntervall) = fraOgMed.isBefore(other.fraOgMed)
    infix fun starterEtter(other: DatoIntervall) = fraOgMed.isAfter(other.fraOgMed)

    infix fun slutterSamtidigEllerTidligere(other: DatoIntervall) = slutterSamtidig(other) || slutterTidligere(other)
    infix fun slutterSamtidigEllerSenere(other: DatoIntervall) = slutterSamtidig(other) || slutterEtter(other)
    infix fun slutterSamtidig(other: DatoIntervall) = tilOgMed.isEqual(other.tilOgMed)
    infix fun slutterTidligere(other: DatoIntervall) = tilOgMed.isBefore(other.tilOgMed)
    infix fun slutterEtter(other: DatoIntervall) = tilOgMed.isAfter(other.tilOgMed)
    infix fun slutterInni(other: DatoIntervall) = (starterSamtidigEllerTidligere(other) || starterEtter(other)) &&
        !før(other) && slutterSamtidigEllerTidligere(other)

    infix fun før(other: DatoIntervall) = tilOgMed.isBefore(other.fraOgMed)
    infix fun etter(other: DatoIntervall) = fraOgMed.isAfter(other.tilOgMed)

    /** Inkluderer første og siste dag. */
    fun antallDager(): Long = ChronoUnit.DAYS.between(fraOgMed, tilOgMed.plusDays(1))
}
