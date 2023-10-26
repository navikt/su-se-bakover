package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.UlikeSimuleringer
import økonomi.domain.simulering.kryssjekk

class KryssjekkSaksbehandlersOgAttestantsSimulering(
    private val saksbehandlersSimulering: Simulering,
    private val attestantsSimulering: Utbetaling.SimulertUtbetaling,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    fun sjekk(): Either<UlikeSimuleringer, Unit> {
        return kontroller().mapLeft {
            logErr(
                saksbehandlersSimulering = saksbehandlersSimulering,
                attestantsSimulering = attestantsSimulering.simulering,
                feil = it,
                log = log,
            )
            it
        }
    }

    private fun kontroller(): Either<UlikeSimuleringer, Unit> {
        return saksbehandlersSimulering.kryssjekk(attestantsSimulering.simulering)
    }
}

private fun logErr(
    saksbehandlersSimulering: Simulering,
    attestantsSimulering: Simulering,
    feil: UlikeSimuleringer,
    log: Logger,
) {
    log.error(
        "Utbetaling kunne ikke gjennomføres, kontrollsimulering er ulik saksbehandlers simulering: ${feil::class}. Se sikkerlogg for detaljer.",
        RuntimeException("Genererer en stacktrace for enklere debugging."),
    )
    sikkerLogg.error(
        "Utbetaling kunne ikke gjennomføres, kontrollsimulering: {}, er ulik saksbehandlers simulering: {}",
        serialize(attestantsSimulering, true),
        serialize(saksbehandlersSimulering, true),
    )
}

sealed interface KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet {
    data object UlikGjelderId : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    data object UlikFeilutbetaling : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    data object UlikPeriode : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    data object UliktBeløp : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet

    data object FantIngenGjeldendeUtbetalingerForDato : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
}
