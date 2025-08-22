package no.nav.su.se.bakover.service.avstemming

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.tid.startOfDay
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingRepo
import økonomi.domain.Fagområde
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AvstemmingServiceImpl(
    private val repo: AvstemmingRepo,
    private val publisher: AvstemmingPublisher,
    private val clock: Clock,
) : AvstemmingService {
    override fun grensesnittsavstemming(fagområde: Fagområde): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
        val periode = GrensesnittsavstemmingPeriodeBuilder(repo.hentSisteGrensesnittsavstemming(fagområde), clock)
            .build(fagområde)
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
        // TODO jah: Hvorfor konverterer vi LocalDate til Tidspunkt her? Periodene er alltid definert som LocalDate. Prøv å bruke LocalDate i stedet.
        val fraOgMed = løpendeFraOgMed.startOfDay(zoneIdOslo)
        // Ble enige med OS/UR om å velge tidspunkt til første mikrosekund etter kjøreplan-datoen. Det er viktig at fraOgMed og tilOgMed er samme måned. Ref: https://nav-it.slack.com/archives/C01CD2WPP1U/p1755598874585169
        val tilOgMed = fraOgMed.plus(1, ChronoUnit.MICROS)
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

data object AvstemmingFeilet
