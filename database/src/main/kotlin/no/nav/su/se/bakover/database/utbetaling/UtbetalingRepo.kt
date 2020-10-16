package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel

interface UtbetalingRepo {
    fun hentUtbetaling(utbetalingId: UUID30): Utbetaling?
    fun hentUtbetaling(avstemmingsnøkkel: Avstemmingsnøkkel): Utbetaling?
    fun oppdaterMedKvittering(utbetalingId: UUID30, kvittering: Kvittering): Utbetaling

    fun opprettUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling): Utbetaling
}
