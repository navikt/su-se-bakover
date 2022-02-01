package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import java.util.UUID

interface UtbetalingRepo {
    fun hentUtbetaling(utbetalingId: UUID30): Utbetaling.OversendtUtbetaling?
    fun hentUtbetalinger(sakId: UUID): List<Utbetaling>
    fun hentUtbetaling(avstemmingsnøkkel: Avstemmingsnøkkel): Utbetaling.OversendtUtbetaling?
    fun oppdaterMedKvittering(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering)
    fun opprettUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering, transactionContext: TransactionContext = defaultTransactionContext())
    fun hentUkvitterteUtbetalinger(): List<Utbetaling.OversendtUtbetaling.UtenKvittering>

    fun defaultTransactionContext(): TransactionContext
}
