package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId

interface OppgaveClient {
    fun opprettOppgave(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId>
    fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId>
    fun lukkOppgave(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit>
    fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit>
    fun oppdaterOppgave(oppgaveId: OppgaveId, beskrivelse: String): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit>
}

// TODO jah: Høres usannsynlig ut at alt dette kan skje i hver av funksjonene.
sealed interface OppgaveFeil {
    object KunneIkkeOppretteOppgave : OppgaveFeil
    object KunneIkkeLukkeOppgave : OppgaveFeil
    object KunneIkkeOppdatereOppgave : OppgaveFeil
    object KunneIkkeEndreOppgave : OppgaveFeil
    object KunneIkkeSøkeEtterOppgave : OppgaveFeil
    object KunneIkkeLageToken : OppgaveFeil
}
