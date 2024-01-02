package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse

interface OppgaveService {

    fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>

    /** Skal kun brukes ved asynkrone kall, der man ikke har tilgang til bruker's JTW */
    fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>

    fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse>

    /** Skal kun brukes ved asynkrone kall, der man ikke har tilgang til bruker's JTW */
    fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse>

    fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        beskrivelse: String,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse>

    /** Skal kun brukes ved asynkrone kall, der man ikke har tilgang til bruker's JTW */
    fun oppdaterOppgaveMedSystembruker(
        oppgaveId: OppgaveId,
        oppdaterOppgaveInfo: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse>

    fun hentOppgave(oppgaveId: OppgaveId): Either<KunneIkkeSøkeEtterOppgave, Oppgave>
}
