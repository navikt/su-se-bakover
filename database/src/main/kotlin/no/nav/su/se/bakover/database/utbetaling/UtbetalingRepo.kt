package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

interface UtbetalingRepo {
    fun hentUtbetaling(utbetalingId: UUID30): Utbetaling?
    fun oppdaterMedKvittering(utbetalingId: UUID30, kvittering: Kvittering): Utbetaling
    fun slettUtbetaling(utbetaling: Utbetaling)
    fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling
    fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Utbetaling
}
