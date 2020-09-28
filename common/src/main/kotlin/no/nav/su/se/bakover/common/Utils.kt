package no.nav.su.se.bakover.common

import java.time.Clock
import java.time.LocalDate
import java.time.Month

fun Int.januar(year: Int) = LocalDate.of(year, Month.JANUARY, this)
fun Int.februar(year: Int) = LocalDate.of(year, Month.FEBRUARY, this)
fun Int.mars(year: Int) = LocalDate.of(year, Month.MARCH, this)
fun Int.april(year: Int) = LocalDate.of(year, Month.APRIL, this)
fun Int.mai(year: Int) = LocalDate.of(year, Month.MAY, this)
fun Int.juni(year: Int) = LocalDate.of(year, Month.JUNE, this)
fun Int.juli(year: Int) = LocalDate.of(year, Month.JULY, this)
fun Int.august(year: Int) = LocalDate.of(year, Month.AUGUST, this)
fun Int.september(year: Int) = LocalDate.of(year, Month.SEPTEMBER, this)
fun Int.oktober(year: Int) = LocalDate.of(year, Month.OCTOBER, this)
fun Int.november(year: Int) = LocalDate.of(year, Month.NOVEMBER, this)
fun Int.desember(year: Int) = LocalDate.of(year, Month.DECEMBER, this)
fun idag(clock: Clock = Clock.systemUTC()) = LocalDate.now(clock)

fun now(clock: Clock = Clock.systemUTC()): Tidspunkt = Tidspunkt.now(clock)

fun LocalDate.startOfDay() = this.atStartOfDay().toTidspunkt()
fun LocalDate.endOfDay() = this.atStartOfDay().plusDays(1).minusNanos(1).toTidspunkt()
fun Tidspunkt.between(start: Tidspunkt, end: Tidspunkt) =
    (this == start || this == end) || this.instant.isAfter(start.instant) && this.instant.isBefore(end.instant)
