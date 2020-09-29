package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

internal class UtbetalingServiceImpl(
    private val repo: UtbetalingRepo
) : UtbetalingService {
    override fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling> {
        return repo.hentUtbetaling(utbetalingId)?.right() ?: FantIkkeUtbetaling.left()
    }
}
