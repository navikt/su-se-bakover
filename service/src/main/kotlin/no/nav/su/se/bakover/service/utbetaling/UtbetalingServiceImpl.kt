package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import org.slf4j.LoggerFactory
import java.util.UUID

internal class UtbetalingServiceImpl(
    private val utbetalingRepo: UtbetalingRepo,
    private val sakRepo: SakRepo,
    private val simuleringClient: SimuleringClient,
    private val utbetalingPublisher: UtbetalingPublisher
) : UtbetalingService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling> {
        return utbetalingRepo.hentUtbetaling(utbetalingId)?.right() ?: FantIkkeUtbetaling.left()
    }

    override fun oppdaterMedKvittering(
        avstemmingsnøkkel: Avstemmingsnøkkel,
        kvittering: Kvittering
    ): Either<FantIkkeUtbetaling, Utbetaling> {
        return utbetalingRepo.hentUtbetaling(avstemmingsnøkkel)
            ?.let {
                if (it is Utbetaling.KvittertUtbetaling) {
                    log.info("Kvittering er allerede mottatt for utbetaling: ${it.id}")
                    it
                } else {
                    utbetalingRepo.oppdaterMedKvittering(it.id, kvittering)
                }.right()
            } ?: FantIkkeUtbetaling.left()
    }

    // TODO incorporate attestant/saksbehandler
    override fun lagUtbetaling(
        sakId: UUID,
        strategy: Oppdrag.UtbetalingStrategy
    ): Utbetaling.UtbetalingForSimulering {
        val sak = sakRepo.hentSak(sakId)!!
        return sak.oppdrag.genererUtbetaling(strategy, sak.fnr)
    }

    override fun simulerUtbetaling(utbetaling: Utbetaling.UtbetalingForSimulering): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        return simuleringClient.simulerUtbetaling(
            OversendelseTilOppdrag.TilSimulering(
                utbetaling = utbetaling,
                avstemmingsnøkkel = Avstemmingsnøkkel()
            )
        ).map { utbetaling.toSimulertUtbetaling(it) }
    }

    override fun utbetal(utbetaling: Utbetaling.SimulertUtbetaling): Either<UtbetalingFeilet, Utbetaling.OversendtUtbetaling> {
        // TODO could/should we always perform consistency at this point?
        return utbetalingPublisher.publish(
            OversendelseTilOppdrag.TilUtbetaling(
                utbetaling = utbetaling,
                avstemmingsnøkkel = Avstemmingsnøkkel()
            )
        ).mapLeft {
            return UtbetalingFeilet.left()
        }.map { oppdragsmelding ->
            val oversendtUtbetaling = utbetaling.toOversendtUtbetaling(oppdragsmelding)
            utbetalingRepo.opprettUtbetaling(oversendtUtbetaling)
            oversendtUtbetaling
        }
    }
}
