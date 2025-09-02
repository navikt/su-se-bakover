package no.nav.su.se.bakover.common.domain.tid

import no.nav.su.se.bakover.common.domain.norwegianLocale
import no.nav.su.se.bakover.common.tid.toTidspunkt
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun min(a: LocalDate, b: LocalDate): LocalDate = if (a < b) a else b
fun max(a: LocalDate, b: LocalDate): LocalDate = if (a > b) a else b

fun LocalDate.førsteINesteMåned(): LocalDate {
    return this.plusMonths(1).startOfMonth()
}

fun LocalDate.sisteIForrigeMåned(): LocalDate {
    return this.minusMonths(1).endOfMonth()
}

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
fun idag(clock: Clock): LocalDate = LocalDate.now(clock)

fun LocalDate.fixedClock(): Clock = startOfDay(ZoneOffset.UTC)
    .let { Clock.fixed(it.instant, ZoneOffset.UTC) }

fun LocalDate.startOfDay(zoneId: ZoneId = zoneIdOslo) = this.atStartOfDay().toTidspunkt(zoneId)
fun LocalDate.endOfDay(zoneId: ZoneId = zoneIdOslo) = this.atStartOfDay().plusDays(1).minusNanos(1).toTidspunkt(zoneId)
fun LocalDate.startOfMonth(): LocalDate = this.withDayOfMonth(1)
fun LocalDate.endOfMonth(): LocalDate = this.withDayOfMonth(this.lengthOfMonth())

fun LocalDate.between(fraOgMed: LocalDate, tilOgMed: LocalDate) =
    (this == fraOgMed || this == tilOgMed) || this.isAfter(fraOgMed) && this.isBefore(tilOgMed)

fun LocalDate.erFørsteDagIMåned() = dayOfMonth == 1
fun LocalDate.erSisteDagIMåned() = dayOfMonth == lengthOfMonth()
fun LocalDate.erMindreEnnEnMånedSenere(localDate: LocalDate) = this.isBefore(localDate.plusMonths(1))
infix fun LocalDate.isEqualOrBefore(other: LocalDate) = !this.isAfter(other)
fun List<LocalDate>.erSortert(): Boolean = this.sorted() == this
fun List<LocalDate>.erUtenDuplikater(): Boolean = this.distinct() == this
fun List<LocalDate>.erSortertOgUtenDuplikater(): Boolean = this.erSortert() && this.erUtenDuplikater()

fun LocalDate.ddMMyyyy(): String = this.format(ddMMyyyyFormatter)
fun LocalDate.toBrevformat(): String = this.format(DateTimeFormatter.ofPattern("d. LLLL yyyy", norwegianLocale))

fun LocalDate.erNesteÅr(): Boolean {
    val currentYear = LocalDate.now().year
    return this.year == currentYear + 1
}

fun LocalDate.erFremITidMenIkkeSammeMåned(): Boolean {
    val naa = LocalDate.now()
    return this.isAfter(naa) && this.month != naa.month
}
