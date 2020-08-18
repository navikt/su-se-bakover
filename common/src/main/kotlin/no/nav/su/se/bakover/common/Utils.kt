package no.nav.su.se.bakover.common

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

// NAIS_CLUSTER_NAME blir satt av Nais.
fun isLocalOrRunningTests(): Boolean = System.getenv()["NAIS_CLUSTER_NAME"] == null

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
fun idag() = LocalDate.now()

// TODO brukbart? - truncate for samme format som databasen har?
fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
