package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either

interface OppgaveClient {
    fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId>
    fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId>
    fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, Unit>
    fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, Unit>
}

object KunneIkkeOppretteOppgave
object KunneIkkeLukkeOppgave
