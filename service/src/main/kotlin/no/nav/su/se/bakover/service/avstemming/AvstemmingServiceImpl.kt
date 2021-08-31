package no.nav.su.se.bakover.service.avstemming

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import java.time.Clock

internal class AvstemmingServiceImpl(
    private val repo: AvstemmingRepo,
    private val publisher: AvstemmingPublisher,
    private val clock: Clock,
) : AvstemmingService {
    override fun grensesnittsavstemming(): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
        val periode = AvstemmingPeriodeBuilder(repo.hentSisteAvstemming(), clock).build()
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
        fraOgMed: Tidspunkt,
        tilOgMed: Tidspunkt,
    ): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming> {
        return konsistensavstemming(AvstemmingsPeriode(fraOgMed, tilOgMed))
    }

    private fun grensesnittsavstemming(periode: AvstemmingsPeriode): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
        val utbetalinger = repo.hentUtbetalingerForAvstemming(periode.fraOgMed, periode.tilOgMed)

        val avstemming = Avstemming.Grensesnittavstemming(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = periode.fraOgMed,
            tilOgMed = periode.tilOgMed,
            utbetalinger = utbetalinger,
        )

        return publisher.publish(avstemming).fold(
            { AvstemmingFeilet.left() },
            {
                repo.opprettAvstemming(it)
                repo.oppdaterAvstemteUtbetalinger(it)
                it.right()
            },
        )
    }

    private fun konsistensavstemming(periode: AvstemmingsPeriode): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming> {
        // TODO select utbetalinger for konsistensavstemming
        val avstemming = Avstemming.Konsistensavstemming(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = periode.fraOgMed,
            tilOgMed = periode.tilOgMed,
            utbetalinger = emptyList(),
        )

        return publisher.publish(avstemming).fold(
            { AvstemmingFeilet.left() },
            {
                repo.opprettAvstemming(it)
                repo.oppdaterAvstemteUtbetalinger(it)
                it.right()
            },
        )
    }
}

object AvstemmingFeilet
