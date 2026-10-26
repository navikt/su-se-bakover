package økonomi.domain.simulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.UtbetalingskvitteringException

interface SimuleringClient {
    fun simulerUtbetaling(utbetalingForSimulering: Utbetaling.UtbetalingForSimulering): Either<SimuleringFeilet, Simulering>
}
sealed interface SimuleringFeilet {
    data class Utbetalingskvittering(val feil: Utbetalinger.Kvitteringsfeil) : SimuleringFeilet
    data object UtenforÅpningstid : SimuleringFeilet
    data object PersonFinnesIkkeITPS : SimuleringFeilet
    data object FinnerIkkeKjøreplanForFraOgMed : SimuleringFeilet
    data object OppdragEksistererIkke : SimuleringFeilet
    data object FunksjonellFeil : SimuleringFeilet
    data object TekniskFeil : SimuleringFeilet
}

/**
 * Blokken skal bare bygge utbetalingen, uten persistens eller andre sideeffekter.
 * Transaksjonsavbrudd skal fortsatt håndteres av kalleren, ikke fanges her.
 */
inline fun <T> lagUtbetalingMedKvitteringskontroll(
    lagUtbetaling: () -> T,
): Either<SimuleringFeilet.Utbetalingskvittering, T> = try {
    lagUtbetaling().right()
} catch (feil: UtbetalingskvitteringException) {
    SimuleringFeilet.Utbetalingskvittering(feil.feil).left()
}
