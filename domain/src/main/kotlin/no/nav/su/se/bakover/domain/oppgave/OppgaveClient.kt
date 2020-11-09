package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either

interface OppgaveClient {
    fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId>
    fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeFerdigstilleOppgave, Unit>
}

object KunneIkkeOppretteOppgave
object KunneIkkeFerdigstilleOppgave
