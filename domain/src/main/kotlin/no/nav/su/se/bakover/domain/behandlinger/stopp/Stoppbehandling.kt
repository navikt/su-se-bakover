package no.nav.su.se.bakover.domain.behandlinger.stopp

import arrow.core.Either
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import java.time.Instant
import java.util.UUID

/**
 * Behandling for midlertidig stopp av ytelsen(e). Det er mulig å starte den igjen etter dette.
 * Den første versjonen stopper alle aktive utbetalinger (de som ikke er utbetalt enda).
 */
sealed class Stoppbehandling {
    abstract val id: UUID
    abstract val opprettet: Instant
    abstract val sakId: UUID
    abstract val status: String



    data class Opprettet(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Instant = now(),
        override val sakId: UUID
    ) : Stoppbehandling() {

        override val status = "OPPRETTET"

        fun simuler(simuleringClient: SimuleringClient): Either<SimuleringFeilet, Simulert> {
            throw NotImplementedError("$simuleringClient")
        }
    }

    data class Simulert(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Instant = now(),
        override val sakId: UUID,
        var utbetaling: Utbetaling
    ) : Stoppbehandling() {

        override val status = "SIMULERT"

        fun simuler(simuleringClient: SimuleringClient): Either<SimuleringFeilet, Simulert> {
            throw NotImplementedError("$simuleringClient")
        }

        fun sendTilAttestering(
            aktørId: AktørId,
            oppgave: OppgaveClient
        ): Either<KunneIkkeOppretteOppgave, TilAttestering> {
            throw NotImplementedError("$aktørId $oppgave")
        }
    }

    data class TilAttestering(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Instant = now(),
        override val sakId: UUID,
        val utbetaling: Utbetaling,
        val attestant: Attestant
    ) : Stoppbehandling() {

        override val status = "TIL_ATTESTERING"

        fun iverksett(
            publisher: UtbetalingPublisher
        ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Iverksatt> {
            throw NotImplementedError("$attestant $publisher")
        }
    }

    data class Iverksatt(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Instant = now(),
        override val sakId: UUID,
        val utbetaling: Utbetaling,
        val attestant: Attestant
    ) : Stoppbehandling() {

        override val status = "IVERKSATT"
    }
}
