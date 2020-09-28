package no.nav.su.se.bakover.service.avstemming

import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class AvstemmingPeriodeBuilder(
    private val sisteAvstemming: Avstemming?,
    private val clock: Clock = Clock.systemUTC()
) {

    fun build() = when (sisteAvstemming) {
        null -> AvstemmingsPeriode(
            fom = 1.januar(2020).startOfDay(),
            tom = LocalDate.now(clock).minusDays(1).endOfDay()
        )
        else -> {
            val start = LocalDate.ofInstant(sisteAvstemming.tom.instant.plus(1, ChronoUnit.DAYS), ZoneOffset.UTC)
            val end = LocalDate.now(clock).minusDays(1)
            AvstemmingsPeriode(
                fom = start.startOfDay(),
                tom = end.endOfDay()
            )
        }
    }
}
