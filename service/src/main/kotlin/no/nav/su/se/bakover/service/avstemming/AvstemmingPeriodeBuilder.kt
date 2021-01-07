package no.nav.su.se.bakover.service.avstemming

import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class AvstemmingPeriodeBuilder(
    private val sisteAvstemming: Avstemming?,
    private val clock: Clock = Clock.systemUTC()
) {

    fun build() = when (sisteAvstemming) {
        null -> AvstemmingsPeriode(
            fraOgMed = 1.januar(2021).startOfDay(zoneId = zoneIdOslo),
            tilOgMed = LocalDate.now(clock).minusDays(1).endOfDay(zoneIdOslo)
        )
        else -> {
            val start = LocalDate.ofInstant(sisteAvstemming.tilOgMed.instant.plus(1, ChronoUnit.DAYS), zoneIdOslo)
            val end = LocalDate.now(clock).minusDays(1)
            AvstemmingsPeriode(
                fraOgMed = start.startOfDay(),
                tilOgMed = end.endOfDay()
            )
        }
    }
}
