package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype

/**
 * Velg ut de verdiene du har lyst til å oppdatere
 */
data class OppdaterOppgaveInfo(
    val beskrivelse: String,
    val oppgavetype: Oppgavetype? = null,
    val status: String? = null,
)

interface OppgaveClient {
    fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
    fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
    fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse>
    fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse>
    fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        beskrivelse: String,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse>

    fun oppdaterOppgaveMedSystembruker(
        oppgaveId: OppgaveId,
        oppdatertOppgaveInfo: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse>

    fun hentOppgave(oppgaveId: OppgaveId): Either<KunneIkkeSøkeEtterOppgave, Oppgave>
    fun hentOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<KunneIkkeSøkeEtterOppgave, Oppgave>
}
