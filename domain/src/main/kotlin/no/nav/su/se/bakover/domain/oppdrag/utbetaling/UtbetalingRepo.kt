package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.utbetaling.Utbetaling
import java.util.UUID

interface UtbetalingRepo {
    fun hentOversendtUtbetalingForUtbetalingId(
        utbetalingId: UUID30,
        sessionContext: SessionContext?,
    ): Utbetaling.OversendtUtbetaling?
    fun hentOversendteUtbetalinger(
        sakId: UUID,
        disableSessionCounter: Boolean = false,
    ): Utbetalinger
    fun hentOversendtUtbetalingForAvstemmingsnøkkel(avstemmingsnøkkel: Avstemmingsnøkkel): Utbetaling.OversendtUtbetaling?
    fun oppdaterMedKvittering(
        utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
        sessionContext: SessionContext?,
    )
    fun opprettUtbetaling(
        utbetaling: Utbetaling.OversendtUtbetaling,
        transactionContext: TransactionContext = defaultTransactionContext(),
    )

    /**
     * Kan ikke bruke [Utbetalinger] i dette tilfellet, da den ikke er beregnet på å bruke et ukomplett sett utbetalinger for en sak.
     */
    fun hentUkvitterteUtbetalinger(): List<Utbetaling.OversendtUtbetaling.UtenKvittering>

    fun defaultTransactionContext(): TransactionContext
}
