package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.util.UUID

interface UtbetalingService {
    fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling>
    fun oppdaterMedKvittering(
        avstemmingsnøkkel: Avstemmingsnøkkel,
        kvittering: Kvittering
    ): Either<FantIkkeUtbetaling, Utbetaling>

    fun lagUtbetaling(sakId: UUID, strategy: Oppdrag.UtbetalingStrategy): Utbetaling.UtbetalingForSimulering
    fun simulerUtbetaling(utbetaling: Utbetaling.UtbetalingForSimulering): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>
    fun utbetal(utbetaling: Utbetaling.SimulertUtbetaling): Either<KunneIkkeUtbetale, Utbetaling.OversendtUtbetaling>
    fun simulerUtbetaling(
        sakId: UUID,
        saksbehandler: NavIdentBruker,
        beregning: Beregning
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>

    fun utbetal(
        sakId: UUID,
        attestant: NavIdentBruker,
        beregning: Beregning,
        simulering: Simulering
    ): Either<KunneIkkeUtbetale, Utbetaling.OversendtUtbetaling>

    fun gjenopptaUtbetalinger(
        sakId: UUID,
        saksbehandler: NavIdentBruker
    ): Either<KunneIkkeGjenopptaUtbetalinger, Sak>
}

object FantIkkeUtbetaling

sealed class KunneIkkeUtbetale {
    object SimuleringHarBlittEndretSidenSaksbehandlerSimulerte : KunneIkkeUtbetale()
    object Protokollfeil : KunneIkkeUtbetale()
    object KunneIkkeSimulere : KunneIkkeUtbetale()
}

sealed class KunneIkkeGjenopptaUtbetalinger {
    object FantIkkeSak : KunneIkkeGjenopptaUtbetalinger()
    object HarIngenOversendteUtbetalinger : KunneIkkeGjenopptaUtbetalinger()
    object SisteUtbetalingErIkkeEnStansutbetaling : KunneIkkeGjenopptaUtbetalinger()
    object SimuleringAvStartutbetalingFeilet : KunneIkkeGjenopptaUtbetalinger()
    object SendingAvUtebetalingTilOppdragFeilet : KunneIkkeGjenopptaUtbetalinger()
}
