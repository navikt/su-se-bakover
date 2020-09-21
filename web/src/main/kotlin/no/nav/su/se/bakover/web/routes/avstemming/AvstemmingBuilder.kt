package no.nav.su.se.bakover.web.routes.avstemming

import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AvstemmingBuilder(
    private val repo: ObjectRepo,
    private val clock: Clock = Clock.systemUTC()
) {
    fun build(): Avstemming {
        val periode = AvstemmingPeriodeBuilder(repo.hentSisteAvstemming()).build()
        val utbetalinger = repo.hentUtbetalingerTilAvstemming(periode.fom, periode.tom)
        return Avstemming(
            opprettet = now(clock),
            fom = periode.fom,
            tom = periode.tom,
            utbetalinger = utbetalinger
        )
    }

    class AvstemmingPeriodeBuilder(
        private val sisteAvstemming: Avstemming?,
        clock: Clock = Clock.systemUTC()
    ) {
        val twoDaysAgo = LocalDate.now(clock).atStartOfDay().minusDays(2).toInstant(ZoneOffset.UTC)
        fun build() = when (sisteAvstemming) {
            null -> AvstemmingsPeriode(
                fom = 1.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC),
                tom = twoDaysAgo
            )
            else -> AvstemmingsPeriode(
                fom = sisteAvstemming.tom,
                tom = twoDaysAgo
            )
        }
    }
}

data class AvstemmingsPeriode(
    val fom: Instant,
    val tom: Instant
)
