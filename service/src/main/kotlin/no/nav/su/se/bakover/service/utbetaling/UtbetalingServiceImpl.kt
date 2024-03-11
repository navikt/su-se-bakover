package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import org.slf4j.LoggerFactory
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.simulering.SimuleringClient
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse
import økonomi.domain.utbetaling.UtbetalingPublisher
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.Utbetalingsrequest
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.LocalDate
import java.util.UUID

class UtbetalingServiceImpl(
    private val utbetalingRepo: UtbetalingRepo,
    private val simuleringClient: SimuleringClient,
    private val utbetalingPublisher: UtbetalingPublisher,
) : UtbetalingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentUtbetalingerForSakId(sakId: UUID): Utbetalinger {
        return utbetalingRepo.hentOversendteUtbetalinger(sakId)
    }

    override fun oppdaterMedKvittering(
        utbetalingId: UUID30,
        kvittering: Kvittering,
        sessionContext: SessionContext?,
    ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering> {
        return utbetalingRepo.hentOversendtUtbetalingForUtbetalingId(utbetalingId, sessionContext)
            ?.let { utbetaling ->
                when (utbetaling) {
                    is Utbetaling.OversendtUtbetaling.MedKvittering -> {
                        log.info("Kvittering er allerede mottatt for utbetaling: ${utbetaling.id}")
                        utbetaling
                    }

                    is Utbetaling.OversendtUtbetaling.UtenKvittering -> {
                        log.info("Oppdaterer utbetaling med kvittering fra Oppdrag")
                        utbetaling.toKvittertUtbetaling(kvittering).also {
                            utbetalingRepo.oppdaterMedKvittering(it, sessionContext)
                        }
                    }
                }.right()
            } ?: FantIkkeUtbetaling.left()
            .also { log.warn("Fant ikke utbetaling med id: $utbetalingId") }
    }

    override fun hentGjeldendeUtbetaling(
        sakId: UUID,
        forDato: LocalDate,
    ): Either<Utbetalinger.FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
        return hentUtbetalingerForSakId(sakId).hentGjeldendeUtbetaling(
            forDato,
        )
    }

    /**
     * TODO jah: Klargjøringa kan ikke feile. Trenger ikke ha Left her.
     */
    override fun klargjørUtbetaling(
        utbetaling: Utbetaling.SimulertUtbetaling,
        transactionContext: TransactionContext,
    ): Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>> {
        return UtbetalingKlargjortForOversendelse(
            utbetaling = utbetaling.forberedOversendelse(transactionContext),
            callback = { utbetalingsrequest ->
                sendUtbetalingTilOS(utbetalingsrequest)
            },
        ).right()
    }

    override fun simulerUtbetaling(
        utbetalingForSimulering: Utbetaling.UtbetalingForSimulering,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        return simuleringClient.simulerUtbetaling(utbetalingForSimulering = utbetalingForSimulering)
            .map { utbetalingForSimulering.toSimulertUtbetaling(it) }
    }

    private fun sendUtbetalingTilOS(
        utbetalingsRequest: Utbetalingsrequest,
    ): Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest> {
        return utbetalingPublisher.publishRequest(utbetalingsRequest)
            .mapLeft {
                UtbetalingFeilet.Protokollfeil
            }
    }

    private fun Utbetaling.SimulertUtbetaling.forberedOversendelse(
        transactionContext: TransactionContext,
    ): Utbetaling.OversendtUtbetaling.UtenKvittering {
        return toOversendtUtbetaling(utbetalingPublisher.generateRequest(this)).also {
            utbetalingRepo.opprettUtbetaling(
                utbetaling = it,
                transactionContext = transactionContext,
            )
        }
    }
}
