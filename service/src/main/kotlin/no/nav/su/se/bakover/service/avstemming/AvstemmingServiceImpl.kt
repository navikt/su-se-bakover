package no.nav.su.se.bakover.service.avstemming

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.oppdrag.Fagområde
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingRepo
import java.time.Clock
import java.time.LocalDate

class AvstemmingServiceImpl(
    private val repo: AvstemmingRepo,
    private val publisher: AvstemmingPublisher,
    private val clock: Clock,
) : AvstemmingService {
    override fun grensesnittsavstemming(fagområde: Fagområde): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
        val periode = GrensesnittsavstemmingPeriodeBuilder(repo.hentSisteGrensesnittsavstemming(fagområde), clock)
            .build()
        return grensesnittsavstemming(periode, fagområde)
    }

    override fun grensesnittsavstemming(
        fraOgMed: Tidspunkt,
        tilOgMed: Tidspunkt,
        fagområde: Fagområde,
    ): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
        val periode = AvstemmingsPeriode(fraOgMed, tilOgMed)
        return grensesnittsavstemming(periode, fagområde)
    }

    override fun konsistensavstemming(
        løpendeFraOgMed: LocalDate,
        fagområde: Fagområde,
    ): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming.Ny> {
        val fraOgMed = løpendeFraOgMed.startOfDay(zoneIdOslo)
        val tilOgMed = løpendeFraOgMed.minusDays(1).endOfDay(zoneIdOslo)

        val utbetalinger = repo.hentUtbetalingerForKonsistensavstemming(
            løpendeFraOgMed = fraOgMed,
            opprettetTilOgMed = tilOgMed,
            fagområde = fagområde,
        )

        val avstemming = Avstemming.Konsistensavstemming.Ny(
            opprettet = Tidspunkt.now(clock),
            løpendeFraOgMed = fraOgMed,
            opprettetTilOgMed = tilOgMed,
            utbetalinger = utbetalinger,
            fagområde = fagområde,
        )

        return publisher.publish(avstemming).fold(
            { AvstemmingFeilet.left() },
            {
                repo.opprettKonsistensavstemming(it)
                it.right()
            },
        )
    }

    override fun konsistensavstemmingUtførtForOgPåDato(dato: LocalDate, fagområde: Fagområde): Boolean {
        return repo.konsistensavstemmingUtførtForOgPåDato(dato, fagområde)
    }

    private fun grensesnittsavstemming(
        periode: AvstemmingsPeriode,
        fagområde: Fagområde,
    ): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
        val utbetalinger = repo.hentUtbetalingerForGrensesnittsavstemming(periode.fraOgMed, periode.tilOgMed, fagområde)

        val avstemming = Avstemming.Grensesnittavstemming(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = periode.fraOgMed,
            tilOgMed = periode.tilOgMed,
            utbetalinger = utbetalinger,
            fagområde = fagområde,
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
