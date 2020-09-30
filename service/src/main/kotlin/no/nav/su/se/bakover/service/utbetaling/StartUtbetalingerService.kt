package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet.FantIkkeSak
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet.HarIngenOversendteUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet.SimuleringAvStartutbetalingFeilet
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet.SisteUtbetalingErIkkeEnStansutbetaling
import org.slf4j.LoggerFactory
import java.util.UUID

class StartUtbetalingerService(
    private val repo: ObjectRepo,
    private val simuleringClient: SimuleringClient,
    private val utbetalingPublisher: UtbetalingPublisher
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun startUtbetalinger(sakId: UUID): Either<StartUtbetalingFeilet, Utbetaling> {

        val sak = repo.hentSak(sakId) ?: return FantIkkeSak.left()

        val sisteOversendteUtbetaling = sak.oppdrag.sisteOversendteUtbetaling()
            ?: return HarIngenOversendteUtbetalinger.left()

        if (!sisteOversendteUtbetaling.erStansutbetaling()) return SisteUtbetalingErIkkeEnStansutbetaling.left()

        val stansetFraOgMed = sisteOversendteUtbetaling.sisteUtbetalingslinje()!!.fom
        val stansetTilOgMed = sisteOversendteUtbetaling.sisteUtbetalingslinje()!!.tom
        check(stansetFraOgMed <= stansetTilOgMed) {
            "Feil ved start av utbetalinger. Stopputbetalingens fraOgMed er etter tilOgMed"
        }

        // Vi må ekskludere alt før nest siste stopp-utbetaling for ikke å duplisere utbetalinger.
        val startIndeks = sak.oppdrag.oversendteUtbetalinger().dropLast(1).indexOfLast {
            it.erStansutbetaling()
        }.let { if (it < 0) 0 else it + 1 } // Ekskluderer den eventuelle stopp-utbetalingen

        val stansetEllerDelvisStansetUtbetalingslinjer = sak.oppdrag.oversendteUtbetalinger()
            .subList(startIndeks, sak.oppdrag.oversendteUtbetalinger().size - 1) // Ekskluderer den siste stopp-utbetalingen
            .flatMap {
                it.utbetalingslinjer
            }.filter {
                // Merk: En utbetalingslinje kan være delvis stanset.
                it.fom.between(stansetFraOgMed, stansetTilOgMed) ||
                    it.tom.between(stansetFraOgMed, stansetTilOgMed)
            }
        check(stansetEllerDelvisStansetUtbetalingslinjer.last().tom == stansetTilOgMed) {
            "Feil ved start av utbetalinger. Stopputbetalingens tilOgMed ($stansetTilOgMed) matcher ikke utbetalingslinja (${stansetEllerDelvisStansetUtbetalingslinjer.last().tom}"
        }

        val utbetaling = Utbetaling(
            utbetalingslinjer = stansetEllerDelvisStansetUtbetalingslinjer.fold(listOf()) { acc, utbetalingslinje ->
                (
                    acc + Utbetalingslinje(
                        fom = maxOf(stansetFraOgMed, utbetalingslinje.fom),
                        tom = utbetalingslinje.tom,
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

        simuleringClient.simulerUtbetaling(nyUtbetaling).fold(
            { return SimuleringAvStartutbetalingFeilet.left() },
            { simulering ->
                sak.oppdrag.leggTilUtbetaling(utbetaling)
                utbetaling.addSimulering(simulering)
            }
        )

        return utbetalingPublisher.publish(nyUtbetaling).fold(
            {
                log.error("Startutbetaling feilet ved publisering av utbetaling")
                utbetaling.addOppdragsmelding(it.oppdragsmelding)
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
