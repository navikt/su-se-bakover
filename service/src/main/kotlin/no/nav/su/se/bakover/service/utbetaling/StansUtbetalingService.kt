package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.UtbetalingStrategy.Stans
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.toOversendtUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class StansUtbetalingService(
    private val clock: Clock = Clock.systemUTC(),
    private val utbetalingPublisher: UtbetalingPublisher,
    private val utbetalingService: UtbetalingService,
    private val sakService: SakService
) {
    object KunneIkkeStanseUtbetalinger

    private val log = LoggerFactory.getLogger(this::class.java)

    fun stansUtbetalinger(
        sakId: UUID,
        saksbehandler: Saksbehandler
    ): Either<KunneIkkeStanseUtbetalinger, Sak> {
        val sak = sakService.hentSak(sakId).getOrElse {
            return KunneIkkeStanseUtbetalinger.left()
        }
        val utbetaling = sak.oppdrag.genererUtbetaling(strategy = Stans(clock), sak.fnr)
        val utbetalingForSimulering = NyUtbetaling(
            oppdrag = sak.oppdrag,
            utbetaling = utbetaling,
            attestant = Attestant(saksbehandler.navIdent),
            avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock))
        )
        val simulertUtbetaling = utbetalingService.simulerUtbetaling(utbetalingForSimulering).fold(
            { return KunneIkkeStanseUtbetalinger.left() },
            { simulertUtbetaling ->
                if (simulertUtbetaling.simulering.nettoBeløp != 0 || simulertUtbetaling.simulering.bruttoYtelse() != 0) {
                    log.error("Simulering av stansutbetaling der vi sendte inn beløp 0, nettobeløp i simulering var ${simulertUtbetaling.simulering.nettoBeløp}, bruttobeløp var:${simulertUtbetaling.simulering.bruttoYtelse()}")
                    return KunneIkkeStanseUtbetalinger.left()
                }
                simulertUtbetaling
            }
        )

        // TODO Her kan vi legge inn transaksjon
        return utbetalingPublisher.publish(
            utbetalingForSimulering.copy(
                utbetaling = simulertUtbetaling
            )
        ).fold(
            {
                log.error("Stansutbetaling feilet ved publisering av utbetaling")
                KunneIkkeStanseUtbetalinger.left()
            },
            {
                utbetalingService.opprettUtbetaling(
                    utbetalingForSimulering.oppdrag.id,
                    simulertUtbetaling.toOversendtUtbetaling(it)
                )
                // TODO Fix
                sakService.hentSak(sakId)
                    .mapLeft { KunneIkkeStanseUtbetalinger }
                    .map { it }
            }
        )
    }
}
