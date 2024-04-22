package økonomi.application.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.KunneIkkeKlaregjøreUtbetaling
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import java.time.LocalDate
import java.util.UUID

interface UtbetalingService {
    fun hentUtbetalingerForSakId(sakId: UUID): Utbetalinger

    fun oppdaterMedKvittering(
        utbetalingId: UUID30,
        kvittering: Kvittering,
        sessionContext: SessionContext?,
    ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering>

    fun simulerUtbetaling(
        utbetalingForSimulering: Utbetaling.UtbetalingForSimulering,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>

    /**
     * Lagrer i databasen og klargjør utbetalingene for oversendelse til OS (lager XML-request)
     * Konsumenten av denne funksjonen er ansvarlig for håndtering av [transactionContext] i tillegg til å kalle [UtbetalingKlargjortForOversendelse.callback]
     * på et hensiktsmessig tidspunkt.
     *
     * @return [UtbetalingKlargjortForOversendelse] inneholder [UtbetalingKlargjortForOversendelse.utbetaling] med generert XML for publisering på kø,
     * i tillegg til [UtbetalingKlargjortForOversendelse.callback] for å publisere utbetalingen på kø mot OS. Kall til denne funksjonen bør gjennomføres
     * som det siste steget i [transactionContext], slik at eventuelle feil her kan rulle tilbake hele transaksjonen.
     */
    fun klargjørUtbetaling(
        utbetaling: Utbetaling.SimulertUtbetaling,
        transactionContext: TransactionContext,
    ): Either<KunneIkkeKlaregjøreUtbetaling, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>

    fun hentGjeldendeUtbetaling(
        sakId: UUID,
        forDato: LocalDate,
    ): Either<Utbetalinger.FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje>
}

data object FantIkkeUtbetaling
