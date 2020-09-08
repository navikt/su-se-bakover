package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.right
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher

object UtbetalingStub : UtbetalingPublisher {
    override fun publish(utbetaling: Utbetaling, oppdragGjelder: Fnr) = Unit.right()
}
