package no.nav.su.se.bakover.common

import no.nav.su.se.bakover.common.periode.Periode
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val zoneIdOslo: ZoneId = ZoneId.of("Europe/Oslo")

fun Int.januar(year: Int): LocalDate = LocalDate.of(year, Month.JANUARY, this)
fun Int.februar(year: Int): LocalDate = LocalDate.of(year, Month.FEBRUARY, this)
fun Int.mars(year: Int): LocalDate = LocalDate.of(year, Month.MARCH, this)
fun Int.april(year: Int): LocalDate = LocalDate.of(year, Month.APRIL, this)
fun Int.mai(year: Int): LocalDate = LocalDate.of(year, Month.MAY, this)
fun Int.juni(year: Int): LocalDate = LocalDate.of(year, Month.JUNE, this)
fun Int.juli(year: Int): LocalDate = LocalDate.of(year, Month.JULY, this)
fun Int.august(year: Int): LocalDate = LocalDate.of(year, Month.AUGUST, this)
fun Int.september(year: Int): LocalDate = LocalDate.of(year, Month.SEPTEMBER, this)
fun Int.oktober(year: Int): LocalDate = LocalDate.of(year, Month.OCTOBER, this)
fun Int.november(year: Int): LocalDate = LocalDate.of(year, Month.NOVEMBER, this)
fun Int.desember(year: Int): LocalDate = LocalDate.of(year, Month.DECEMBER, this)
fun idag(clock: Clock = Clock.systemUTC()): LocalDate = LocalDate.now(clock)

fun LocalDate.startOfDay(zoneId: ZoneId = zoneIdOslo) = this.atStartOfDay().toTidspunkt(zoneId)
fun LocalDate.endOfDay(zoneId: ZoneId = zoneIdOslo) = this.atStartOfDay().plusDays(1).minusNanos(1).toTidspunkt(zoneId)
fun LocalDate.startOfMonth(): LocalDate = this.withDayOfMonth(1)
fun LocalDate.endOfMonth(): LocalDate = this.withDayOfMonth(this.lengthOfMonth())
fun LocalDate.between(periode: Periode) = this.between(periode.fraOgMed, periode.tilOgMed)
fun LocalDate.between(fraOgMed: LocalDate, tilOgMed: LocalDate) =
    (this == fraOgMed || this == tilOgMed) || this.isAfter(fraOgMed) && this.isBefore(tilOgMed)
fun LocalDate.erFørsteDagIMåned() = dayOfMonth == 1
fun LocalDate.erSisteDagIMåned() = dayOfMonth == lengthOfMonth()

fun Tidspunkt.between(fraOgMed: Tidspunkt, tilOgMed: Tidspunkt) =
    (this == fraOgMed || this == tilOgMed) || this.instant.isAfter(fraOgMed.instant) && this.instant.isBefore(tilOgMed.instant)

fun LocalDate.ddMMyyyy(): String = this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
