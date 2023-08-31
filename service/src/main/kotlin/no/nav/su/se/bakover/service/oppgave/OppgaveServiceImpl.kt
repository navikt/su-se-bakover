package no.nav.su.se.bakover.service.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.oppgave.domain.Oppgave

class OppgaveServiceImpl(
    private val oppgaveClient: OppgaveClient,
) : OppgaveService {

    override fun opprettOppgave(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> {
        return oppgaveClient.opprettOppgave(config)
    }

    override fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> {
        return oppgaveClient.opprettOppgaveMedSystembruker(config)
    }

    override fun lukkOppgave(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> {
        return oppgaveClient.lukkOppgave(oppgaveId)
    }

    override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> {
        return oppgaveClient.lukkOppgaveMedSystembruker(oppgaveId)
    }

    override fun oppdaterOppgave(oppgaveId: OppgaveId, beskrivelse: String): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit> {
        return oppgaveClient.oppdaterOppgave(oppgaveId, beskrivelse)
    }

    override fun hentOppgave(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeSøkeEtterOppgave, Oppgave> {
        return oppgaveClient.hentOppgave(oppgaveId)
    }
}
