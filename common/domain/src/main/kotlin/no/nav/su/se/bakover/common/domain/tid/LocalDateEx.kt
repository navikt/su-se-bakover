package no.nav.su.se.bakover.common.domain.tid

import no.nav.su.se.bakover.common.tid.periode.Måned
import java.time.LocalDate

fun min(a: LocalDate, b: LocalDate): LocalDate = if (a < b) a else b
fun max(a: LocalDate, b: LocalDate): LocalDate = if (a > b) a else b

/**
 * @throws IllegalArgumentException dersom dato ikke er den 1. i måneden.
 */
fun LocalDate.toMåned(): Måned = Måned.fra(this)
