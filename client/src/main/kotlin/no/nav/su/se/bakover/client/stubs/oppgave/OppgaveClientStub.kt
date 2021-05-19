package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import kotlin.random.Random

object OppgaveClientStub : OppgaveClient {

    override fun opprettOppgave(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> =
        generateOppgaveId().right()

    override fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> =
        generateOppgaveId().right()

    override fun lukkOppgave(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> = Unit.right()

    override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> =
        Unit.right()

    override fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        beskrivelse: String,
    ): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit> =
        Unit.right()

    private fun generateOppgaveId() = OppgaveId(Random.nextLong(0, Long.MAX_VALUE).toString())
}
