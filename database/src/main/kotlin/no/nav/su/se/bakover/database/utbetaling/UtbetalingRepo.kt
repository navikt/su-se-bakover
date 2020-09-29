package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

interface UtbetalingRepo {
    fun hentUtbetaling(utbetalingId: UUID30): Utbetaling?
}
