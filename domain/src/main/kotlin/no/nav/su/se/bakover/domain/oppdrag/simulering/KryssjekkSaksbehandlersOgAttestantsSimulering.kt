package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.simulering.Simulering
import økonomi.domain.utbetaling.Utbetaling

class KryssjekkSaksbehandlersOgAttestantsSimulering(
    private val saksbehandlersSimulering: Simulering,
    private val attestantsSimulering: Utbetaling.SimulertUtbetaling,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    fun sjekk(): Either<KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet, Unit> {
        return kontroller().mapLeft {
            logInfo(
                saksbehandlersSimulering = saksbehandlersSimulering,
                attestantsSimulering = attestantsSimulering.simulering,
                feil = it,
                log = log,
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
        if (saksbehandlersSimulering.hentFeilutbetalteBeløp() != attestantsSimulering.simulering.hentFeilutbetalteBeløp()) {
            return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikFeilutbetaling.left()
        }

        if (saksbehandlersSimulering.hentTotalUtbetaling()
                .måneder() != attestantsSimulering.simulering.hentTotalUtbetaling().måneder()
        ) {
            return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikPeriode.left()
        }

        if (saksbehandlersSimulering.hentTotalUtbetaling() != attestantsSimulering.simulering.hentTotalUtbetaling()) {
            return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UliktBeløp.left()
        }

        return Unit.right()
    }
}

private fun logInfo(
    saksbehandlersSimulering: Simulering,
    attestantsSimulering: Simulering,
    feil: KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet,
    log: Logger,
) {
    log.info(
        "Utbetaling kunne ikke gjennomføres, kontrollsimulering er ulik saksbehandlers simulering: ${feil::class}. Se sikkerlogg for detaljer.",
        RuntimeException("Genererer en stacktrace for enklere debugging."),
    )
    sikkerLogg.info(
        "Utbetaling kunne ikke gjennomføres, kontrollsimulering: {}, er ulik saksbehandlers simulering: {}",
        attestantsSimulering.toString(),
        saksbehandlersSimulering.toString(),
    )
}

sealed interface KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet {
    data object UlikGjelderId : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    data object UlikFeilutbetaling : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    data object UlikPeriode : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    data object UliktBeløp : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet

    data object FantIngenGjeldendeUtbetalingerForDato : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
}
