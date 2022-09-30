package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.FeilVedKryssjekkAvTidslinjerOgSimulering

interface SimuleringClient {
    fun simulerUtbetaling(request: SimulerUtbetalingRequest): Either<SimuleringFeilet, Simulering>
}
sealed interface SimuleringFeilet {
    object UtenforÅpningstid : SimuleringFeilet
    object PersonFinnesIkkeITPS : SimuleringFeilet
    object FinnerIkkeKjøreplanForFraOgMed : SimuleringFeilet
    object OppdragEksistererIkke : SimuleringFeilet
    object FunksjonellFeil : SimuleringFeilet
    object TekniskFeil : SimuleringFeilet
    data class KontrollAvSimuleringFeilet(val feil: FeilVedKryssjekkAvTidslinjerOgSimulering) : SimuleringFeilet
}
