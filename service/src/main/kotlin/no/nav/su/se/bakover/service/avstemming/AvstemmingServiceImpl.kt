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
    override fun avstemming(): Either<AvstemmingFeilet, Avstemming> {
        val periode = AvstemmingPeriodeBuilder(repo.hentSisteAvstemming(), clock).build()
        return gjørAvstemming(periode)
    }

    override fun avstemming(fraOgMed: Tidspunkt, tilOgMed: Tidspunkt): Either<AvstemmingFeilet, Avstemming> {
        val periode = AvstemmingsPeriode(fraOgMed, tilOgMed)
        return gjørAvstemming(periode)
    }

    private fun gjørAvstemming(periode: AvstemmingsPeriode): Either<AvstemmingFeilet, Avstemming> {
        val utbetalinger = repo.hentUtbetalingerForAvstemming(periode.fraOgMed, periode.tilOgMed)

        val avstemming = Avstemming(
            opprettet = Tidspunkt.now(),
            fraOgMed = periode.fraOgMed,
            tilOgMed = periode.tilOgMed,
            utbetalinger = utbetalinger
        )

        return publisher.publish(avstemming).fold(
            { AvstemmingFeilet.left() },
            {
                repo.opprettAvstemming(it)
                repo.oppdaterAvstemteUtbetalinger(it)
                it.right()
            }
        )
    }
}

object AvstemmingFeilet
