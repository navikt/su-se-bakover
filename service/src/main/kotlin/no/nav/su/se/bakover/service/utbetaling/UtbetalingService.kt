package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.oppdrag.FantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
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
        request: SimulerUtbetalingRequest.NyUtbetalingRequest,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>

    fun simulerOpphør(
        request: SimulerUtbetalingRequest.OpphørRequest,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>

    /**
     * Oppretter nye utbetalinger, lagrer i databasen og klargjør utbetalingene for oversendelse til OS (lager XML-request)
     * Konsumenten av denne funksjonen er ansvarlig for håndtering av [transactionContext] i tillegg til å kalle [UtbetalingKlargjortForOversendelse.callback]
     * på et hensiktsmessig tidspunkt.
     *
     * @return [UtbetalingKlargjortForOversendelse] inneholder [UtbetalingKlargjortForOversendelse.utbetaling] med generert XML for publisering på kø,
     * i tillegg til [UtbetalingKlargjortForOversendelse.callback] for å publisere utbetalingen på kø mot OS. Kall til denne funksjonen bør gjennomføres
     * som det siste steget i [transactionContext], slik at eventuelle feil her kan rulle tilbake hele transaksjonen.
     */
    fun klargjørNyUtbetaling(
        request: UtbetalRequest.NyUtbetaling,
        transactionContext: TransactionContext,
    ): Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>

    fun simulerStans(
        request: SimulerUtbetalingRequest.StansRequest,
    ): Either<SimulerStansFeilet, Utbetaling.SimulertUtbetaling>

    /**
     * Oppretter utbetalinger for stans, lagrer i databasen og klargjør utbetalingene for oversendelse til OS (lager XML-request)
     * Konsumenten av denne funksjonen er ansvarlig for håndtering av [transactionContext] i tillegg til å kalle [UtbetalingKlargjortForOversendelse.callback]
     * på et hensiktsmessig tidspunkt.
     *
     * @return [UtbetalingKlargjortForOversendelse] inneholder [UtbetalingKlargjortForOversendelse.utbetaling] med generert XML for publisering på kø,
     * i tillegg til [UtbetalingKlargjortForOversendelse.callback] for å publisere utbetalingen på kø mot OS. Kall til denne funksjonen bør gjennomføres
     * som det siste steget i [transactionContext], slik at eventuelle feil her kan rulle tilbake hele transaksjonen.
     */
    fun klargjørStans(
        request: UtbetalRequest.Stans,
        transactionContext: TransactionContext,
    ): Either<UtbetalStansFeil, UtbetalingKlargjortForOversendelse<UtbetalStansFeil.KunneIkkeUtbetale>>

    fun simulerGjenopptak(
        request: SimulerUtbetalingRequest.GjenopptakRequest,
    ): Either<SimulerGjenopptakFeil, Utbetaling.SimulertUtbetaling>

    /**
     * Oppretter utbetalinger for gjenopptak, lagrer i databasen og klargjør utbetalingene for oversendelse til OS (lager XML-request)
     * Konsumenten av denne funksjonen er ansvarlig for håndtering av [transactionContext] i tillegg til å kalle [UtbetalingKlargjortForOversendelse.callback]
     * på et hensiktsmessig tidspunkt.
     *
     * @return [UtbetalingKlargjortForOversendelse] inneholder [UtbetalingKlargjortForOversendelse.utbetaling] med generert XML for publisering på kø,
     * i tillegg til [UtbetalingKlargjortForOversendelse.callback] for å publisere utbetalingen på kø mot OS. Kall til denne funksjonen bør gjennomføres
     * som det siste steget i [transactionContext], slik at eventuelle feil her kan rulle tilbake hele transaksjonen.
     */
    fun klargjørGjenopptak(
        request: UtbetalRequest.Gjenopptak,
        transactionContext: TransactionContext,
    ): Either<UtbetalGjenopptakFeil, UtbetalingKlargjortForOversendelse<UtbetalGjenopptakFeil.KunneIkkeUtbetale>>

    /**
     * Oppretter utbetalinger for opphør, lagrer i databasen og klargjør utbetalingene for oversendelse til OS (lager XML-request)
     * Konsumenten av denne funksjonen er ansvarlig for håndtering av [transactionContext] i tillegg til å kalle [UtbetalingKlargjortForOversendelse.callback]
     * på et hensiktsmessig tidspunkt.
     *
     * @return [UtbetalingKlargjortForOversendelse] inneholder [UtbetalingKlargjortForOversendelse.utbetaling] med generert XML for publisering på kø,
     * i tillegg til [UtbetalingKlargjortForOversendelse.callback] for å publisere utbetalingen på kø mot OS. Kall til denne funksjonen bør gjennomføres
     * som det siste steget i [transactionContext], slik at eventuelle feil her kan rulle tilbake hele transaksjonen.
     */
    fun klargjørOpphør(
        request: UtbetalRequest.Opphør,
        transactionContext: TransactionContext,
    ): Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>

    fun hentGjeldendeUtbetaling(
        sakId: UUID,
        forDato: LocalDate,
    ): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje>
}

object FantIkkeUtbetaling

sealed class SimulerGjenopptakFeil {
    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : SimulerGjenopptakFeil()
    data class KunneIkkeGenerereUtbetaling(val feil: Utbetalingsstrategi.Gjenoppta.Feil) : SimulerGjenopptakFeil()
}

sealed class UtbetalGjenopptakFeil {
    data class KunneIkkeSimulere(val feil: SimulerGjenopptakFeil) : UtbetalGjenopptakFeil()
    data class KunneIkkeUtbetale(val feil: UtbetalingFeilet) : UtbetalGjenopptakFeil()
}

sealed class SimulerStansFeilet {
    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : SimulerStansFeilet()
    data class KunneIkkeGenerereUtbetaling(val feil: Utbetalingsstrategi.Stans.Feil) : SimulerStansFeilet()
}

sealed class UtbetalStansFeil {
    data class KunneIkkeSimulere(val feil: SimulerStansFeilet) : UtbetalStansFeil()
    data class KunneIkkeUtbetale(val feil: UtbetalingFeilet) : UtbetalStansFeil()
}
