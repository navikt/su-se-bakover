package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.UtbetalingStrategy.Gjenoppta
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
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
        val sisteOversendteUtbetaling = sak.oppdrag.sisteOversendteUtbetaling()
            ?: return StartUtbetalingFeilet.HarIngenOversendteUtbetalinger.left()

        if (Utbetaling.UtbetalingType.STANS != sisteOversendteUtbetaling.type) return StartUtbetalingFeilet.SisteUtbetalingErIkkeEnStansutbetaling.left()

        val utbetaling = sak.oppdrag.genererUtbetaling(Gjenoppta, sak.fnr)

        val nyUtbetaling = OversendelseTilOppdrag.NyUtbetaling(
            oppdrag = sak.oppdrag,
            utbetaling = utbetaling,
            attestant = Attestant("SU"), // TODO: Bruk saksbehandler
            avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock))
        )

        val simulertUtbetaling = utbetalingService.simulerUtbetaling(nyUtbetaling).fold(
            { return StartUtbetalingFeilet.SimuleringAvStartutbetalingFeilet.left() },
            { it }
        )

        return utbetalingService.utbetal(
            OversendelseTilOppdrag.TilUtbetaling(
                nyUtbetaling.oppdrag,
                utbetaling = simulertUtbetaling,
                attestant = nyUtbetaling.attestant,
                avstemmingsnøkkel = nyUtbetaling.avstemmingsnøkkel
            )
        ).fold(
            {
                log.error("Startutbetaling feilet ved publisering av utbetaling")
                StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet.left()
            },
            {
                return sakService.hentSak(sakId)
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
