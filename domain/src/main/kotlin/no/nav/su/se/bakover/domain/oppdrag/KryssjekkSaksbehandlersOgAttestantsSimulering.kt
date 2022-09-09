package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.Clock

class KryssjekkSaksbehandlersOgAttestantsSimulering(
    private val saksbehandlersSimulering: Simulering,
    private val attestantsSimulering: Utbetaling.SimulertUtbetaling,
    private val clock: Clock,
) {

    fun sjekk(): Either<KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet, Unit> {
        return kontroller().mapLeft {
            logErr(
                saksbehandlersSimulering = saksbehandlersSimulering,
                attestantsSimulering = attestantsSimulering.simulering,
                feil = it
            )
            it
        }
    }

    private fun kontroller(): Either<KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet, Unit> {
        if (saksbehandlersSimulering.gjelderId != attestantsSimulering.simulering.gjelderId) {
            return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikGjelderId.left()
        }
        if (saksbehandlersSimulering.harFeilutbetalinger() != attestantsSimulering.simulering.harFeilutbetalinger()) {
            return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikFeilutbetaling.left()
        }

        val tolketSaksbehandlers = saksbehandlersSimulering.tolk()
        val tolketAttestants = attestantsSimulering.simulering.tolk()
        val utbetalingstidslinje = attestantsSimulering.utbetalingslinjer.tidslinje(clock = clock)
            .getOrHandle { throw RuntimeException("Kunne ikke konstruere utbetalingstidslinje!") }

        if (!tolketSaksbehandlers.erTomSimulering() && !tolketAttestants.erTomSimulering()) {
            if (tolketSaksbehandlers.periode() != tolketAttestants.periode()) {
                return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikPeriode.left()
            }
        } else {
            if (!(tolketSaksbehandlers.erTomSimulering() && tolketAttestants.erTomSimulering())) {
                return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikPeriode.left()
            }
        }

        tolketSaksbehandlers.simulertePerioder.forEach { tolketPeriode ->
            val utbetalingFraTidslinje = utbetalingstidslinje.gjeldendeForDato(tolketPeriode.periode.fraOgMed)
                ?: return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.FantIngenGjeldendeUtbetalingerForDato.left()

            if (tolketSaksbehandlers.hentØnsketUtbetaling(tolketPeriode.periode).sum() != tolketAttestants.hentØnsketUtbetaling(tolketPeriode.periode).sum()) {
                return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UliktBeløp.left()
            }
            if (tolketSaksbehandlers.hentØnsketUtbetaling(tolketPeriode.periode).sum() != utbetalingFraTidslinje.beløp) {
                return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UliktBeløpFraTidslinje.left()
            }
            if (tolketAttestants.hentØnsketUtbetaling(tolketPeriode.periode).sum() != utbetalingFraTidslinje.beløp) {
                return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UliktBeløpFraTidslinje.left()
            }
        }
        return Unit.right()
    }
}

private fun logErr(
    saksbehandlersSimulering: Simulering,
    attestantsSimulering: Simulering,
    feil: KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
) {
    log.error("Utbetaling kunne ikke gjennomføres, kontrollsimulering er ulik saksbehandlers simulering: ${feil::class}. Se sikkerlogg for detaljer.")
    sikkerLogg.error(
        "Utbetaling kunne ikke gjennomføres, kontrollsimulering: {}, er ulik saksbehandlers simulering: {}",
        objectMapper.writeValueAsString(attestantsSimulering),
        objectMapper.writeValueAsString(saksbehandlersSimulering),
    )
}

sealed interface KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet {
    object UlikGjelderId : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    object UlikFeilutbetaling : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    object UlikPeriode : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    object UliktBeløp : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    object UliktBeløpFraTidslinje : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet

    object FantIngenGjeldendeUtbetalingerForDato : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
}
