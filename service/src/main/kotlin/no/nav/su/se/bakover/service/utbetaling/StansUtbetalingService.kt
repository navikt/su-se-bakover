package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsperiode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.utbetaling.StansUtbetalingService.ValidertStansUtbetaling.Companion.validerStansUtbetaling
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class StansUtbetalingService(
    private val simuleringClient: SimuleringClient,
    private val clock: Clock = Clock.systemUTC(),
    private val utbetalingPublisher: UtbetalingPublisher,
    private val utbetalingService: UtbetalingService
) {
    object KunneIkkeStanseUtbetalinger

    private val log = LoggerFactory.getLogger(this::class.java)

    fun stansUtbetalinger(
        sak: Sak
    ): Either<KunneIkkeStanseUtbetalinger, Utbetaling> {
        val stansesFraOgMed = idag(clock).with(TemporalAdjusters.firstDayOfNextMonth()) // neste mnd eller umiddelbart?
        val validertStansUtbetaling = sak.oppdrag.validerStansUtbetaling(stansesFraOgMed)
        require(validertStansUtbetaling.isValid()) {
            "Kan ikke stanse utbetaling: $validertStansUtbetaling"
        }
        val stansesTilOgMed = sak.oppdrag.sisteOversendteUtbetaling()!!.sisteUtbetalingslinje()!!.tilOgMed

        val utbetaling = sak.oppdrag.genererUtbetaling(
            utbetalingsperioder = listOf(
                Utbetalingsperiode(
                    fraOgMed = stansesFraOgMed,
                    tilOgMed = stansesTilOgMed,
                    beløp = 0,
                )
            ),
            fnr = sak.fnr
        )
        val utbetalingForSimulering = NyUtbetaling(
            oppdrag = sak.oppdrag,
            utbetaling = utbetaling,
            attestant = Attestant("SU") // Det er ikke nødvendigvis valgt en attestant på dette tidspunktet.
        )
        val simulertUtbetaling = simuleringClient.simulerUtbetaling(utbetalingForSimulering).fold(
            { return KunneIkkeStanseUtbetalinger.left() },
            { simulering ->
                if (simulering.nettoBeløp != 0) {
                    log.error("Simulering av stansutbetaling der vi sendte inn beløp 0, men nettobeløp i simulering var ${simulering.nettoBeløp}")
                    return KunneIkkeStanseUtbetalinger.left()
                }
                utbetalingService.opprettUtbetaling(sak.oppdrag.id, utbetaling)
                utbetalingService.addSimulering(utbetaling.id, simulering)
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
                utbetalingService.addOppdragsmelding(
                    utbetaling.id,
                    it.oppdragsmelding
                )
                KunneIkkeStanseUtbetalinger.left()
            },
            {
                utbetalingService.addOppdragsmelding(utbetaling.id, it).right()
            }
        )
    }

    data class ValidertStansUtbetaling(
        val harUtbetalingerSomKanStanses: Boolean,
        val harBeløpOver0: Boolean
    ) {
        companion object {
            fun Oppdrag.validerStansUtbetaling(stansesFraDato: LocalDate) = ValidertStansUtbetaling(
                harUtbetalingerSomKanStanses = harOversendteUtbetalingerEtter(stansesFraDato),
                harBeløpOver0 = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()?.let {
                    // TODO jah: I en annen pull-request bør vi utvide en utbetaling til å være en sealed class med de forskjellig typene utbetaling.
                    it.beløp > 0 // Stopputbetalinger vil ha beløp 0. Vi ønsker ikke å stoppe en stopputbetaling.
                } ?: true
            )
        }

        fun isValid() = harUtbetalingerSomKanStanses && harBeløpOver0
    }
}
