package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.util.UUID

interface UtbetalingService {
    fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling>
    fun oppdaterMedKvittering(
        avstemmingsnøkkel: Avstemmingsnøkkel,
        kvittering: Kvittering
    ): Either<FantIkkeUtbetaling, Utbetaling>

    fun lagUtbetaling(sakId: UUID, strategy: Oppdrag.UtbetalingStrategy): OversendelseTilOppdrag.TilSimulering
    fun simulerUtbetaling(utbetaling: OversendelseTilOppdrag.TilSimulering): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>
    fun utbetal(utbetaling: OversendelseTilOppdrag.TilUtbetaling): Either<UtbetalingFeilet, Utbetaling.OversendtUtbetaling>
}

object FantIkkeUtbetaling
object UtbetalingFeilet
