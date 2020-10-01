package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding

interface UtbetalingPublisher {
    fun publish(
        nyUtbetaling: NyUtbetaling
    ): Either<KunneIkkeSendeUtbetaling, Oppdragsmelding>

    data class KunneIkkeSendeUtbetaling(
        val oppdragsmelding: Oppdragsmelding
    ) {
        constructor(
            originalMelding: String,
            tidspunkt: Tidspunkt = now()
        ) : this(Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.FEIL, originalMelding, tidspunkt))
    }
}
