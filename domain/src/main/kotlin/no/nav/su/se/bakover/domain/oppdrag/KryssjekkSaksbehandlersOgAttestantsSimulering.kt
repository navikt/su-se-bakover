package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class KryssjekkSaksbehandlersOgAttestantsSimulering(
    private val saksbehandlersSimulering: Simulering,
    private val attestantsSimulering: Utbetaling.SimulertUtbetaling,
) {

    fun sjekk(): Either<KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet, Unit> {
        return kontroller().mapLeft {
            logErr(
                saksbehandlersSimulering = saksbehandlersSimulering,
                attestantsSimulering = attestantsSimulering.simulering,
                feil = it,
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

        if (saksbehandlersSimulering.hentTotalUtbetaling()
                .måneder() != attestantsSimulering.simulering.hentTotalUtbetaling().måneder()
        ) {
            return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikPeriode.left()
        }

        if (saksbehandlersSimulering.hentTotalUtbetaling() != attestantsSimulering.simulering.hentTotalUtbetaling()) {
            return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UliktBeløp.left()
        }

        if (saksbehandlersSimulering.hentTotalUtbetaling() != attestantsSimulering.simulering.hentTotalUtbetaling()) {
            return KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UliktBeløp.left()
        }

        return Unit.right()
    }
}

private fun logErr(
    saksbehandlersSimulering: Simulering,
    attestantsSimulering: Simulering,
    feil: KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet,
    log: Logger = LoggerFactory.getLogger("SuUserPlugin.kt"),
) {
    log.error(
        "Utbetaling kunne ikke gjennomføres, kontrollsimulering er ulik saksbehandlers simulering: ${feil::class}. Se sikkerlogg for detaljer.",
        RuntimeException("Genererer en stacktrace for enklere debugging."),
    )
    sikkerLogg.error(
        "Utbetaling kunne ikke gjennomføres, kontrollsimulering: {}, er ulik saksbehandlers simulering: {}",
        serialize(attestantsSimulering),
        serialize(saksbehandlersSimulering),
    )
}

sealed interface KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet {
    object UlikGjelderId : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    object UlikFeilutbetaling : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    object UlikPeriode : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
    object UliktBeløp : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet

    object FantIngenGjeldendeUtbetalingerForDato : KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
}
