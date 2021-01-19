package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel

interface UtbetalingRepo {
    fun hentUtbetaling(utbetalingId: UUID30): Utbetaling.OversendtUtbetaling?
    fun hentUtbetaling(avstemmingsnøkkel: Avstemmingsnøkkel): Utbetaling.OversendtUtbetaling?
    fun oppdaterMedKvittering(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering)
    fun opprettUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering)
}
