package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import java.util.UUID

interface UtbetalingRepo {
    fun hentOversendtUtbetalingForUtbetalingId(utbetalingId: UUID30): Utbetaling.OversendtUtbetaling?
    fun hentOversendteUtbetalinger(sakId: UUID): Utbetalinger
    fun hentOversendtUtbetalingForAvstemmingsnøkkel(avstemmingsnøkkel: Avstemmingsnøkkel): Utbetaling.OversendtUtbetaling?
    fun oppdaterMedKvittering(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering)
    fun opprettUtbetaling(
        utbetaling: Utbetaling.OversendtUtbetaling,
        transactionContext: TransactionContext = defaultTransactionContext(),
    )
    fun hentUkvitterteUtbetalinger(): Utbetalinger

    fun defaultTransactionContext(): TransactionContext
}
