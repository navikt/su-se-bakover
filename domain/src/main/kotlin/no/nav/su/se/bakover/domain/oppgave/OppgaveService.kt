package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.oppgave.domain.Oppgave

interface OppgaveService {
    fun opprettOppgave(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId>
    fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId>
    fun lukkOppgave(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit>
    fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit>
    fun oppdaterOppgave(oppgaveId: OppgaveId, beskrivelse: String): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit>
    fun oppdaterOppgave(oppgaveId: OppgaveId, oppdaterOppgaveInfo: OppdaterOppgaveInfo): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit>
    fun hentOppgave(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeSøkeEtterOppgave, Oppgave>
}
