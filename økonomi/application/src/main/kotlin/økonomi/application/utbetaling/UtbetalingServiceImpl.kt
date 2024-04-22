package økonomi.application.utbetaling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import org.slf4j.LoggerFactory
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.simulering.SimuleringClient
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.KunneIkkeKlaregjøreUtbetaling
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

    override fun klargjørUtbetaling(
        utbetaling: Utbetaling.SimulertUtbetaling,
        transactionContext: TransactionContext,
    ): Either<KunneIkkeKlaregjøreUtbetaling, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>> {
        return UtbetalingKlargjortForOversendelse(
            utbetaling = utbetaling.forberedOversendelse(transactionContext).getOrElse {
                return it.left()
            },
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
    ): Either<KunneIkkeKlaregjøreUtbetaling, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        val utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering = Either.catch {
            toOversendtUtbetaling(utbetalingPublisher.generateRequest(this))
        }.getOrElse {
            return KunneIkkeKlaregjøreUtbetaling.KunneIkkeLageUtbetalingslinjer(it).left()
        }
        return Either.catch {
            utbetalingRepo.opprettUtbetaling(
                utbetaling = utbetaling,
                transactionContext = transactionContext,
            )
            utbetaling
        }.mapLeft {
            KunneIkkeKlaregjøreUtbetaling.KunneIkkeLagre(it)
        }
    }
}
