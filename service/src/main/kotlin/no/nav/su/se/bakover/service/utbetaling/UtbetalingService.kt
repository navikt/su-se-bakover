package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

interface UtbetalingService {
    fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling>
    fun oppdaterMedKvittering(utbetalingId: UUID30, kvittering: Kvittering): Either<FantIkkeUtbetaling, Utbetaling>
    fun slettUtbetaling(utbetaling: Utbetaling)
    fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling
    fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Utbetaling
    fun addOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding): Utbetaling
}

object FantIkkeUtbetaling
