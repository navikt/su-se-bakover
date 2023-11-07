package no.nav.su.se.bakover.common.domain.tid

import java.time.LocalDate

fun min(a: LocalDate, b: LocalDate): LocalDate = if (a < b) a else b
fun max(a: LocalDate, b: LocalDate): LocalDate = if (a > b) a else b
