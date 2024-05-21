package no.nav.su.se.bakover.service.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeSøkeEtterOppgave
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse

class OppgaveServiceImpl(
    private val oppgaveClient: OppgaveClient,
) : OppgaveService {

    override fun opprettOppgave(
        config: OppgaveConfig,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return oppgaveClient.opprettOppgave(config)
    }

    override fun opprettOppgaveMedSystembruker(
        config: OppgaveConfig,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return oppgaveClient.opprettOppgaveMedSystembruker(config)
    }

    override fun lukkOppgave(oppgaveId: OppgaveId, tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse> {
        return oppgaveClient.lukkOppgave(oppgaveId, tilordnetRessurs)
    }

    override fun lukkOppgaveMedSystembruker(
        oppgaveId: OppgaveId,
        tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
    ): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse> {
        return oppgaveClient.lukkOppgaveMedSystembruker(oppgaveId, tilordnetRessurs)
    }

    override fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        oppdaterOppgaveInfo: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse> {
        return oppgaveClient.oppdaterOppgave(oppgaveId, oppdaterOppgaveInfo)
    }

    override fun oppdaterOppgaveMedSystembruker(
        oppgaveId: OppgaveId,
        oppdaterOppgaveInfo: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse> {
        return oppgaveClient.oppdaterOppgaveMedSystembruker(oppgaveId, oppdaterOppgaveInfo)
    }

    override fun hentOppgave(oppgaveId: OppgaveId): Either<KunneIkkeSøkeEtterOppgave, Oppgave> {
        return oppgaveClient.hentOppgave(oppgaveId)
    }
}
