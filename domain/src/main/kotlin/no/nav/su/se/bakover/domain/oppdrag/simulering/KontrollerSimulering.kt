package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import org.slf4j.LoggerFactory
import java.time.Clock

data class KontrollerSimulering(
    private val simulertUtbetaling: Utbetaling.SimulertUtbetaling,
    private val eksisterendeUtbetalinger: List<Utbetaling>,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    val resultat: Either<KontrollAvSimuleringFeilet, Utbetaling.SimulertUtbetaling> by lazy {
        try {
            val tolketSimulering = simulertUtbetaling.simulering.tolk().also { tolketSimulering ->
                if (tolketSimulering.harFeilutbetalinger()) {
                    log.error("Simulering inneholder feilutbetalinger, se sikkerlogg for simulering")
                    sikkerLogg.error(objectMapper.writeValueAsString(simulertUtbetaling.simulering))
                    return@lazy KontrollAvSimuleringFeilet.SimuleringInneholderFeilutbetaling.left()
                }
            }

            if (simulertUtbetaling.simulering.periodeList.isNotEmpty()) {
                val utbetalingerInkludertNy = eksisterendeUtbetalinger + simulertUtbetaling
                val tidslinjeInkludertNyUtbetaling = TidslinjeForUtbetalinger(
                    periode = Periode.create(
                        simulertUtbetaling.simulering.periodeList.minOf { it.fraOgMed },
                        simulertUtbetaling.simulering.periodeList.maxOf { it.tilOgMed },
                    ),
                    utbetalingslinjer = utbetalingerInkludertNy.flatMap { it.utbetalingslinjer },
                    clock = clock,
                )

                val tolkedeUtbetalinger = tolketSimulering.simulertePerioder
                    .map { it.utbetaling }
                    .flatMap { it.tolketDetalj }
                    .filterIsInstance<TolketDetalj.Ordinær>()

                tolkedeUtbetalinger.forEach { tolket ->
                    tidslinjeInkludertNyUtbetaling.gjeldendeForDato(tolket.fraOgMed)
                        ?.let { beløpFraTidslinje ->
                            if (tolket.beløp != beløpFraTidslinje.beløp) {
                                log.error("Ikke samsvar mellom beløp i simulering og beløp på tidslinje, se sikkerlogg for detaljer.")
                                sikkerLogg.error(objectMapper.writeValueAsString(simulertUtbetaling.simulering))
                                sikkerLogg.error(tidslinjeInkludertNyUtbetaling.toString())
                                return@lazy KontrollAvSimuleringFeilet.SimulertBeløpErForskjelligFraBeløpPåTidslinje.left()
                            }
                        }
                }
            }
        } catch (ex: TolketUtbetaling.IngenEntydigTolkning) {
            log.error("Fanget exception ved kontroll av simulering, se sikkerlogg for simulering", ex)
            sikkerLogg.error(objectMapper.writeValueAsString(simulertUtbetaling.simulering))
            return@lazy KontrollAvSimuleringFeilet.KunneIkkeTolkeSimulering.left()
        } catch (ex: TolketUtbetaling.IndikererFeilutbetaling) {
            log.error("Fanget exception ved kontroll av simulering, se sikkerlogg for simulering", ex)
            sikkerLogg.error(objectMapper.writeValueAsString(simulertUtbetaling.simulering))
            return@lazy KontrollAvSimuleringFeilet.KunneIkkeTolkeSimulering.left()
        }
        return@lazy simulertUtbetaling.right()
    }

    sealed class KontrollAvSimuleringFeilet {
        object KunneIkkeTolkeSimulering : KontrollAvSimuleringFeilet()
        object SimuleringInneholderFeilutbetaling : KontrollAvSimuleringFeilet()
        object SimulertBeløpErForskjelligFraBeløpPåTidslinje : KontrollAvSimuleringFeilet()
    }
}
