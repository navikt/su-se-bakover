package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.right
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingClient

object UtbetalingStub : UtbetalingClient {
    override fun sendUtbetaling(utbetaling: Utbetaling, oppdragGjelder: String) = Unit.right()
}
