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
    data object KunneIkkeOppretteOppgave : OppgaveFeil
    data object KunneIkkeLukkeOppgave : OppgaveFeil
    data object KunneIkkeOppdatereOppgave : OppgaveFeil
    data object KunneIkkeEndreOppgave : OppgaveFeil
    data object KunneIkkeSøkeEtterOppgave : OppgaveFeil
    data object KunneIkkeLageToken : OppgaveFeil
}
