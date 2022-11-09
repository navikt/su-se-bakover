package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.oppdrag.FantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.LocalDate
import java.util.UUID

interface UtbetalingService {
    fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling>
    fun hentUtbetalingerForSakId(sakId: UUID): List<Utbetaling>

    fun oppdaterMedKvittering(
        utbetalingId: UUID30,
        kvittering: Kvittering,
    ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering>

    fun simulerUtbetaling(
        utbetaling: Utbetaling.UtbetalingForSimulering,
        simuleringsperiode: Periode,
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
    ): Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>

    fun hentGjeldendeUtbetaling(
        sakId: UUID,
        forDato: LocalDate,
    ): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje>
}

object FantIkkeUtbetaling
