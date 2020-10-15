package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
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

    fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling.OversendtUtbetaling): Utbetaling

    fun lagUtbetaling(sakId: UUID, strategy: Oppdrag.UtbetalingStrategy): NyUtbetaling
    fun simulerUtbetaling(utbetaling: NyUtbetaling): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>
}

object FantIkkeUtbetaling
