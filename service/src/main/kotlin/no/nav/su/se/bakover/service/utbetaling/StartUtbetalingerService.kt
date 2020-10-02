package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.util.UUID

class StartUtbetalingerService(
    private val simuleringClient: SimuleringClient,
    private val utbetalingPublisher: UtbetalingPublisher,
    private val utbetalingService: UtbetalingService,
    private val sakService: SakService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun startUtbetalinger(sakId: UUID): Either<StartUtbetalingFeilet, Utbetaling> {
        val sak = sakService.hentSak(sakId).fold(
            { return StartUtbetalingFeilet.FantIkkeSak.left() },
            { it }
        )
        val sisteOversendteUtbetaling = sak.oppdrag.sisteOversendteUtbetaling()
            ?: return StartUtbetalingFeilet.HarIngenOversendteUtbetalinger.left()

        if (!sisteOversendteUtbetaling.erStansutbetaling()) return StartUtbetalingFeilet.SisteUtbetalingErIkkeEnStansutbetaling.left()

        val stansetFraOgMed = sisteOversendteUtbetaling.sisteUtbetalingslinje()!!.fraOgMed
        val stansetTilOgMed = sisteOversendteUtbetaling.sisteUtbetalingslinje()!!.tilOgMed
        check(stansetFraOgMed <= stansetTilOgMed) {
            "Feil ved start av utbetalinger. Stopputbetalingens fraOgMed er etter tilOgMed"
        }

        // Vi må ekskludere alt før nest siste stopp-utbetaling for ikke å duplisere utbetalinger.
        val startIndeks = sak.oppdrag.oversendteUtbetalinger().dropLast(1).indexOfLast {
            it.erStansutbetaling()
        }.let { if (it < 0) 0 else it + 1 } // Ekskluderer den eventuelle stopp-utbetalingen

        val stansetEllerDelvisStansetUtbetalingslinjer = sak.oppdrag.oversendteUtbetalinger()
            .subList(
                startIndeks,
                sak.oppdrag.oversendteUtbetalinger().size - 1
            ) // Ekskluderer den siste stopp-utbetalingen
            .flatMap {
                it.utbetalingslinjer
            }.filter {
                // Merk: En utbetalingslinje kan være delvis stanset.
                it.fraOgMed.between(stansetFraOgMed, stansetTilOgMed) ||
                    it.fraOgMed.between(stansetFraOgMed, stansetTilOgMed)
            }
        check(stansetEllerDelvisStansetUtbetalingslinjer.last().tilOgMed == stansetTilOgMed) {
            "Feil ved start av utbetalinger. Stopputbetalingens tilOgMed ($stansetTilOgMed) matcher ikke utbetalingslinja (${stansetEllerDelvisStansetUtbetalingslinjer.last().tilOgMed}"
        }

        val utbetaling = Utbetaling(
            utbetalingslinjer = stansetEllerDelvisStansetUtbetalingslinjer.fold(listOf()) { acc, utbetalingslinje ->
                (
                    acc + Utbetalingslinje(
                        fraOgMed = maxOf(stansetFraOgMed, utbetalingslinje.fraOgMed),
                        tilOgMed = utbetalingslinje.tilOgMed,
                        forrigeUtbetalingslinjeId = acc.lastOrNull()?.id
                            ?: sisteOversendteUtbetaling.sisteUtbetalingslinje()!!.id,
                        beløp = utbetalingslinje.beløp
                    )
                    )
            },
            fnr = sak.fnr
        )

        val nyUtbetaling = NyUtbetaling(
            oppdrag = sak.oppdrag,
            utbetaling = utbetaling,
            attestant = Attestant("SU") // Det er ikke nødvendigvis valgt en attestant på dette tidspunktet.
        )

        val simulertUtbetaling = simuleringClient.simulerUtbetaling(nyUtbetaling).fold(
            { return StartUtbetalingFeilet.SimuleringAvStartutbetalingFeilet.left() },
            { simulering ->
                utbetalingService.opprettUtbetaling(sak.oppdrag.id, utbetaling)
                utbetalingService.addSimulering(utbetaling.id, simulering)
            }
        )

        return utbetalingPublisher.publish(
            nyUtbetaling.copy(
                utbetaling = simulertUtbetaling
            )
        ).fold(
            {
                log.error("Startutbetaling feilet ved publisering av utbetaling")
                utbetalingService.addOppdragsmelding(utbetaling.id, it.oppdragsmelding)
                StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet.left()
            },
            {
                utbetalingService.addOppdragsmelding(utbetaling.id, it).right()
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
