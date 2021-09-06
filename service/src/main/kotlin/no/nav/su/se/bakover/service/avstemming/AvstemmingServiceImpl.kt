package no.nav.su.se.bakover.service.avstemming

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.igår
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.database.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import java.time.Clock
import java.time.LocalDate

internal class AvstemmingServiceImpl(
    private val repo: AvstemmingRepo,
    private val publisher: AvstemmingPublisher,
    private val clock: Clock,
) : AvstemmingService {
    override fun grensesnittsavstemming(): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
        val periode = GrensesnittsavstemmingPeriodeBuilder(repo.hentSisteGrensesnittsavstemming(), clock).build()
        return grensesnittsavstemming(periode)
    }

    override fun grensesnittsavstemming(
        fraOgMed: Tidspunkt,
        tilOgMed: Tidspunkt,
    ): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
        val periode = AvstemmingsPeriode(fraOgMed, tilOgMed)
        return grensesnittsavstemming(periode)
    }

    override fun konsistensavstemming(
        løpendeFraOgMed: LocalDate,
    ): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming> {
        val løpFraOgMed = løpendeFraOgMed.startOfDay(zoneIdOslo)
        val opprettetTilOgMed = igår().endOfDay(zoneIdOslo)

        val utbetalinger = repo.hentUtbetalingerForKonsistensavstemming(
            løpendeFraOgMed = løpFraOgMed,
            opprettetTilOgMed = opprettetTilOgMed,
        )

        val avstemming = Avstemming.Konsistensavstemming.Ny(
            opprettet = Tidspunkt.now(clock),
            løpendeFraOgMed = løpFraOgMed,
            opprettetTilOgMed = opprettetTilOgMed,
            utbetalinger = utbetalinger,
        )

        return publisher.publish(avstemming).fold(
            { AvstemmingFeilet.left() },
            {
                repo.opprettKonsistensavstemming(it)
                it.right()
            },
        )
    }

    private fun grensesnittsavstemming(periode: AvstemmingsPeriode): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
        val utbetalinger = repo.hentUtbetalingerForGrensesnittsavstemming(periode.fraOgMed, periode.tilOgMed)

        val avstemming = Avstemming.Grensesnittavstemming(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = periode.fraOgMed,
            tilOgMed = periode.tilOgMed,
            utbetalinger = utbetalinger,
        )

        return publisher.publish(avstemming).fold(
            { AvstemmingFeilet.left() },
            {
                repo.opprettGrensesnittsavstemming(it)
                repo.oppdaterUtbetalingerEtterGrensesnittsavstemming(it)
                it.right()
            },
        )
    }
}

object AvstemmingFeilet
