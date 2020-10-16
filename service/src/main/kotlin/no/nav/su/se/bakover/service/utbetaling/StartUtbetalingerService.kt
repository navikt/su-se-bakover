package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.UtbetalingStrategy.Gjenoppta
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class StartUtbetalingerService(
    private val utbetalingService: UtbetalingService,
    private val sakService: SakService,
    private val clock: Clock = Clock.systemUTC()
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun startUtbetalinger(sakId: UUID): Either<StartUtbetalingFeilet, Sak> {
        val sak = sakService.hentSak(sakId).fold(
            { return StartUtbetalingFeilet.FantIkkeSak.left() },
            { it }
        )
        // TODO implement guards in strategy
        // val sisteOversendteUtbetaling = sak.oppdrag.sisteOversendteUtbetaling()
        //     ?: return StartUtbetalingFeilet.HarIngenOversendteUtbetalinger.left()
        // if (Utbetaling.UtbetalingType.STANS != sisteOversendteUtbetaling.type) return StartUtbetalingFeilet.SisteUtbetalingErIkkeEnStansutbetaling.left()

        val nyUtbetaling = utbetalingService.lagUtbetaling(sak.id, Gjenoppta(behandler = NavIdentBruker.Attestant("SU"))) // TODO pass actual

        val simulertUtbetaling = utbetalingService.simulerUtbetaling(nyUtbetaling).fold(
            { return StartUtbetalingFeilet.SimuleringAvStartutbetalingFeilet.left() },
            { it }
        )

        return utbetalingService.utbetal(
            OversendelseTilOppdrag.TilUtbetaling(
                utbetaling = simulertUtbetaling,
                avstemmingsnøkkel = nyUtbetaling.avstemmingsnøkkel
            )
        ).fold(
            {
                log.error("Startutbetaling feilet ved publisering av utbetaling")
                StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet.left()
            },
            {
                sakService.hentSak(sakId)
                    .mapLeft { StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet }
                    .map { it }
            }
        )
    }
}

sealed class StartUtbetalingFeilet {
    object FantIkkeSak : StartUtbetalingFeilet()
    object HarIngenOversendteUtbetalinger : StartUtbetalingFeilet()
    object SisteUtbetalingErIkkeEnStansutbetaling : StartUtbetalingFeilet()
    object SimuleringAvStartutbetalingFeilet : StartUtbetalingFeilet()
    object SendingAvUtebetalingTilOppdragFeilet : StartUtbetalingFeilet()
}
