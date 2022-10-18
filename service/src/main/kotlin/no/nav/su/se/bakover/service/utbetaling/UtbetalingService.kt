package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.oppdrag.FantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.FeilVedKryssjekkAvTidslinjerOgSimulering
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
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
        eksisterendeUtbetalinger: List<Utbetaling>,
        beregningsperiode: Periode,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>
    fun simulerOpphør(
        utbetaling: Utbetaling.UtbetalingForSimulering,
        eksisterendeUtbetalinger: List<Utbetaling>,
        opphørsperiode: Periode,
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
        utbetaling: Utbetaling.UtbetalingForSimulering,
        eksisterendeUtbetalinger: List<Utbetaling>,
        beregningsperiode: Periode,
        saksbehandlersSimulering: Simulering,
        transactionContext: TransactionContext,
    ): Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>

    fun simulerStans(
        utbetaling: Utbetaling.UtbetalingForSimulering,
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
        utbetaling: Utbetaling.UtbetalingForSimulering,
        saksbehandlersSimulering: Simulering,
        transactionContext: TransactionContext,
    ): Either<UtbetalStansFeil, UtbetalingKlargjortForOversendelse<UtbetalStansFeil.KunneIkkeUtbetale>>

    fun simulerGjenopptak(
        utbetaling: Utbetaling.UtbetalingForSimulering,
        eksisterendeUtbetalinger: List<Utbetaling>,
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
        utbetaling: Utbetaling.UtbetalingForSimulering,
        eksisterendeUtbetalinger: List<Utbetaling>,
        saksbehandlersSimulering: Simulering,
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
        utbetaling: Utbetaling.UtbetalingForSimulering,
        eksisterendeUtbetalinger: List<Utbetaling>,
        opphørsperiode: Periode,
        saksbehandlersSimulering: Simulering,
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

    data class KontrollFeilet(val feil: FeilVedKryssjekkAvTidslinjerOgSimulering.Gjenopptak) : SimulerGjenopptakFeil()
}

sealed class UtbetalGjenopptakFeil {
    data class KunneIkkeSimulere(val feil: SimulerGjenopptakFeil) : UtbetalGjenopptakFeil()
    data class KunneIkkeUtbetale(val feil: UtbetalingFeilet) : UtbetalGjenopptakFeil()
}

sealed class SimulerStansFeilet {
    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : SimulerStansFeilet()
    data class KunneIkkeGenerereUtbetaling(val feil: Utbetalingsstrategi.Stans.Feil) : SimulerStansFeilet()

    data class KontrollFeilet(val feil: FeilVedKryssjekkAvTidslinjerOgSimulering.Stans) : SimulerStansFeilet()
}

sealed class UtbetalStansFeil {
    data class KunneIkkeSimulere(val feil: SimulerStansFeilet) : UtbetalStansFeil()
    data class KunneIkkeUtbetale(val feil: UtbetalingFeilet) : UtbetalStansFeil()
}
