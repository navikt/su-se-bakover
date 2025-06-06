package no.nav.su.se.bakover.service.avstemming

import no.nav.su.se.bakover.common.domain.tid.endOfDay
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.fraOgMed
import no.nav.su.se.bakover.common.domain.tid.startOfDay
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import økonomi.domain.Fagområde
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class GrensesnittsavstemmingPeriodeBuilder(
    private val sisteAvstemming: Avstemming.Grensesnittavstemming?,
    private val clock: Clock,
) {

    fun build(fagområde: Fagområde) = when (sisteAvstemming) {
        null -> AvstemmingsPeriode(
            fraOgMed = when (fagområde) {
                Fagområde.SUALDER -> 1.juni(2025).startOfDay(zoneId = zoneIdOslo)
                Fagområde.SUUFORE -> 1.januar(2021).startOfDay(zoneId = zoneIdOslo)
            },
            tilOgMed = LocalDate.now(clock).minusDays(1).endOfDay(zoneIdOslo),
        )
        else -> {
            val start = LocalDate.ofInstant(sisteAvstemming.tilOgMed.instant.plus(1, ChronoUnit.DAYS), zoneIdOslo)
            val end = LocalDate.now(clock).minusDays(1)
            AvstemmingsPeriode(
                fraOgMed = start.startOfDay(zoneId = zoneIdOslo),
                tilOgMed = end.endOfDay(zoneId = zoneIdOslo),
            )
        }
    }
}
