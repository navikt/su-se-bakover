package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag

interface SimuleringClient {
    fun simulerUtbetaling(tilSimulering: OversendelseTilOppdrag.TilSimulering): Either<SimuleringFeilet, Simulering>
}
