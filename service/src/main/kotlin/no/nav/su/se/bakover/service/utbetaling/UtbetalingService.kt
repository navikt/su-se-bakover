package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.LocalDate
import java.util.UUID

interface UtbetalingService {
    fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling>
    fun hentUtbetalinger(sakId: UUID): List<Utbetaling>
    fun oppdaterMedKvittering(
        avstemmingsnøkkel: Avstemmingsnøkkel,
        kvittering: Kvittering,
    ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering>

    fun simulerUtbetaling(
        sakId: UUID,
        saksbehandler: NavIdentBruker,
        beregning: Beregning,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>

    fun simulerOpphør(
        sakId: UUID,
        saksbehandler: NavIdentBruker,
        opphørsdato: LocalDate,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>

    fun utbetal(
        sakId: UUID,
        attestant: NavIdentBruker,
        beregning: Beregning,
        simulering: Simulering,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    ): Either<UtbetalingFeilet, Utbetaling.OversendtUtbetaling.UtenKvittering>

    fun publiserUtbetaling(
        utbetaling: Utbetaling.SimulertUtbetaling
    ): Either<UtbetalingFeilet, Utbetalingsrequest>

    fun lagreUtbetaling(
        utbetaling: Utbetaling.SimulertUtbetaling,
        transactionContext: TransactionContext? = null
    ): Utbetaling.OversendtUtbetaling.UtenKvittering

    fun genererUtbetalingsRequest(
        sakId: UUID,
        attestant: NavIdentBruker,
        beregning: Beregning,
        simulering: Simulering,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    ): Either<UtbetalingFeilet, Utbetaling.SimulertUtbetaling>

    fun simulerStans(
        sakId: UUID,
        saksbehandler: NavIdentBruker,
        stansDato: LocalDate,
    ): Either<SimulerStansFeilet, Utbetaling.SimulertUtbetaling>

    fun stansUtbetalinger(
        sakId: UUID,
        attestant: NavIdentBruker,
        simulering: Simulering,
        stansDato: LocalDate,
    ): Either<UtbetalStansFeil, Utbetaling.OversendtUtbetaling.UtenKvittering>

    fun simulerGjenopptak(
        sak: Sak,
        saksbehandler: NavIdentBruker,
    ): Either<SimulerGjenopptakFeil, Utbetaling.SimulertUtbetaling>

    fun gjenopptaUtbetalinger(
        sakId: UUID,
        attestant: NavIdentBruker,
        simulering: Simulering,
    ): Either<UtbetalGjenopptakFeil, Utbetaling.OversendtUtbetaling.UtenKvittering>

    fun opphør(
        sakId: UUID,
        attestant: NavIdentBruker,
        simulering: Simulering,
        opphørsdato: LocalDate,
    ): Either<UtbetalingFeilet, Utbetaling.OversendtUtbetaling.UtenKvittering>

    fun hentGjeldendeUtbetaling(
        sakId: UUID,
        forDato: LocalDate,
    ): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje>
}

object FantIkkeUtbetaling
object FantIkkeGjeldendeUtbetaling

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
