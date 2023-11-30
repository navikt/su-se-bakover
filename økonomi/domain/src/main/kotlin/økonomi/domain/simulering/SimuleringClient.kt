package økonomi.domain.simulering

import arrow.core.Either
import økonomi.domain.utbetaling.Utbetaling

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
