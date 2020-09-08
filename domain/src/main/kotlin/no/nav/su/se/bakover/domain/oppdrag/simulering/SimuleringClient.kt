package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

interface SimuleringClient {
    fun simulerUtbetaling(oppdrag: Oppdrag, utbetaling: Utbetaling, simuleringGjelder: Fnr): Either<SimuleringFeilet, Simulering>
}
