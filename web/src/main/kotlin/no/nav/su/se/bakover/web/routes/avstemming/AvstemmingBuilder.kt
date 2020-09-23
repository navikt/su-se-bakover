package no.nav.su.se.bakover.web.routes.avstemming

import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class AvstemmingBuilder(
    private val repo: ObjectRepo,
    private val clock: Clock = Clock.systemUTC()
) {
    fun build(): Avstemming {
        val periode = AvstemmingPeriodeBuilder(repo.hentSisteAvstemming()).build()
        val utbetalinger = repo.hentUtebetalingerForAvstemming(periode.fom, periode.tom)
        return Avstemming(
            opprettet = now(clock),
            fom = periode.fom,
            tom = periode.tom,
            utbetalinger = utbetalinger
        )
    }

    class AvstemmingPeriodeBuilder(
        private val sisteAvstemming: Avstemming?,
        private val clock: Clock = Clock.systemUTC()
    ) {

        fun build() = when (sisteAvstemming) {
            null -> AvstemmingsPeriode(
                fom = 1.januar(2020).startOfDay(),
                tom = LocalDate.now(clock).minusDays(1).endOfDay()
            )
            else -> {
                val start = LocalDate.ofInstant(sisteAvstemming.tom.plus(1, ChronoUnit.DAYS), ZoneOffset.UTC)
                val end = LocalDate.now(clock).minusDays(1)
                AvstemmingsPeriode(
                    fom = start.startOfDay(),
                    tom = end.endOfDay()
                )
            }
        }
    }
}

data class AvstemmingsPeriode(
    val fom: Instant,
    val tom: Instant
)
