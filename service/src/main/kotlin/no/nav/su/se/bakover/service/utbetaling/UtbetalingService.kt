package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.FEIL
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet.FantIkkeSak
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet.HarIngenOversendteUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet.SimuleringAvStartutbetalingFeilet
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet.SisteUtbetalingErIkkeEnStansutbetaling
import org.slf4j.LoggerFactory
import java.util.UUID

class UtbetalingService(
    private val repo: ObjectRepo,
    private val simuleringClient: SimuleringClient,
    private val utbetalingPublisher: UtbetalingPublisher
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun startUtbetalinger(sakId: UUID): Either<StartUtbetalingFeilet, Utbetaling> {
        val sak = repo.hentSak(sakId) ?: return FantIkkeSak.left()
        val sistSendteUtbetaling =
            sak.oppdrag.sisteOversendteUtbetaling() ?: return HarIngenOversendteUtbetalinger.left()
        if (!sistSendteUtbetaling.erStansutbetaling()) return SisteUtbetalingErIkkeEnStansutbetaling.left()

        val stansetFraOgMed = sistSendteUtbetaling.sisteUtbetalingslinje()!!.fom
        // val stansetTilOgMed = sistSendteUtbetaling.sisteUtbetalingslinje()!!.tom

        val utbetalingslinjer = sak.oppdrag.hentUtbetalinger()
            .filterNot { it.erStansutbetaling() }
            .flatMap {
                it.utbetalingslinjer
            }.filter { it.tom >= stansetFraOgMed }

        val utbetaling = Utbetaling(
            utbetalingslinjer = utbetalingslinjer,
            fnr = sak.fnr
        )

        val nyUtbetaling = NyUtbetaling(
            oppdrag = sak.oppdrag,
            utbetaling = utbetaling,
            attestant = Attestant("SU") // Det er ikke nødvendigvis valgt en attestant på dette tidspunktet.
        )

        simuleringClient.simulerUtbetaling(nyUtbetaling).fold(
            { return SimuleringAvStartutbetalingFeilet.left() },
            { simulering ->
                if (simulering.periodeList.size != utbetalingslinjer.size) {
                    log.error("Simulering av startutbetaling har ${simulering.periodeList.size} linjer, mens vi sendte ${utbetalingslinjer.size}")
                    return SimuleringAvStartutbetalingFeilet.left()
                }
                sak.oppdrag.leggTilUtbetaling(utbetaling)
                utbetaling.addSimulering(simulering)
            }
        )

        return utbetalingPublisher.publish(nyUtbetaling).fold(
            {
                log.error("Startutbetaling feilet ved publisering av utbetaling")
                utbetaling.addOppdragsmelding(Oppdragsmelding(FEIL, it.originalMelding))
                SendingAvUtebetalingTilOppdragFeilet.left()
            },
            {
                utbetaling.addOppdragsmelding(it)
                utbetaling.right()
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
