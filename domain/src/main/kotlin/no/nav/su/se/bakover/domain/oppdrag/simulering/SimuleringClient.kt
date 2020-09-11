package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling

interface SimuleringClient {
    fun simulerUtbetaling(nyUtbetaling: NyUtbetaling): Either<SimuleringFeilet, Simulering>
}
