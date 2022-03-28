package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.oppdrag.FantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.LocalDate
import java.util.UUID

interface UtbetalingService {
    fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling>
    fun hentUtbetalingerForSakId(sakId: UUID): List<Utbetaling>
    fun oppdaterMedKvittering(
        avstemmingsnøkkel: Avstemmingsnøkkel,
        kvittering: Kvittering,
    ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering>

    fun simulerUtbetaling(
        request: SimulerUtbetalingRequest.NyUtbetalingRequest,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>

    fun simulerOpphør(
        request: SimulerUtbetalingRequest.OpphørRequest,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>

    fun publiserUtbetaling(
        utbetaling: Utbetaling.SimulertUtbetaling
    ): Either<UtbetalingFeilet, Utbetalingsrequest>

    fun lagreUtbetaling(
        utbetaling: Utbetaling.SimulertUtbetaling,
        transactionContext: TransactionContext? = null
    ): Utbetaling.OversendtUtbetaling.UtenKvittering

    fun verifiserOgSimulerUtbetaling(
        request: UtbetalRequest.NyUtbetaling,
    ): Either<UtbetalingFeilet, Utbetaling.SimulertUtbetaling>

    fun simulerStans(
        request: SimulerUtbetalingRequest.StansRequest,
    ): Either<SimulerStansFeilet, Utbetaling.SimulertUtbetaling>

    fun stansUtbetalinger(
        request: UtbetalRequest.Stans,
    ): Either<UtbetalStansFeil, Utbetaling.OversendtUtbetaling.UtenKvittering>

    fun simulerGjenopptak(
        request: SimulerUtbetalingRequest.GjenopptakRequest,
    ): Either<SimulerGjenopptakFeil, Utbetaling.SimulertUtbetaling>

    fun gjenopptaUtbetalinger(
        request: UtbetalRequest.Gjenopptak,
    ): Either<UtbetalGjenopptakFeil, Utbetaling.OversendtUtbetaling.UtenKvittering>

    fun verifiserOgSimulerOpphør(
        request: UtbetalRequest.Opphør,
    ): Either<UtbetalingFeilet, Utbetaling.SimulertUtbetaling>

    fun hentGjeldendeUtbetaling(
        sakId: UUID,
        forDato: LocalDate,
    ): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje>
}

object FantIkkeUtbetaling

sealed class SimulerGjenopptakFeil {
    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : SimulerGjenopptakFeil()
    data class KunneIkkeGenerereUtbetaling(val feil: Utbetalingsstrategi.Gjenoppta.Feil) : SimulerGjenopptakFeil()
}

sealed class UtbetalGjenopptakFeil {
    data class KunneIkkeSimulere(val feil: SimulerGjenopptakFeil) : UtbetalGjenopptakFeil()
    data class KunneIkkeUtbetale(val feil: UtbetalingFeilet) : UtbetalGjenopptakFeil()
}

sealed class SimulerStansFeilet {
    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : SimulerStansFeilet()
    data class KunneIkkeGenerereUtbetaling(val feil: Utbetalingsstrategi.Stans.Feil) : SimulerStansFeilet()
}

sealed class UtbetalStansFeil {
    data class KunneIkkeSimulere(val feil: SimulerStansFeilet) : UtbetalStansFeil()
    data class KunneIkkeUtbetale(val feil: UtbetalingFeilet) : UtbetalStansFeil()
}
