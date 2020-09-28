package no.nav.su.se.bakover.service.avstemming

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.database.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher

internal class AvstemmingServiceImpl(
    private val repo: AvstemmingRepo,
    private val publisher: AvstemmingPublisher
) : AvstemmingService {
    override fun avstemming(): Either<AvstemmingFeilet, Avstemming> {
        val periode = AvstemmingPeriodeBuilder(repo.hentSisteAvstemming()).build()
        val utbetalinger = repo.hentUtbetalingerForAvstemming(periode.fom, periode.tom)

        val avstemming = Avstemming(
            opprettet = now(),
            fom = periode.fom,
            tom = periode.tom,
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
