package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import økonomi.domain.simulering.Simulering

interface SimuleringClient {
    fun simulerUtbetaling(utbetalingForSimulering: Utbetaling.UtbetalingForSimulering): Either<SimuleringFeilet, Simulering>
}
sealed interface SimuleringFeilet {
    data object UtenforÅpningstid : SimuleringFeilet
    data object PersonFinnesIkkeITPS : SimuleringFeilet
    data object FinnerIkkeKjøreplanForFraOgMed : SimuleringFeilet
    data object OppdragEksistererIkke : SimuleringFeilet
    data object FunksjonellFeil : SimuleringFeilet
    data object TekniskFeil : SimuleringFeilet
}
