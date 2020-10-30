package no.nav.su.se.bakover.service.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeFerdigstilleOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId

internal class OppgaveServiceImpl(
    private val oppgaveClient: OppgaveClient
) : OppgaveService {

    override fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId> {
        return oppgaveClient.opprettOppgave(config)
    }

    override fun ferdigstillFørstegangsoppgave(aktørId: AktørId): Either<KunneIkkeFerdigstilleOppgave, Unit> {
        return oppgaveClient.ferdigstillFørstegangsoppgave(aktørId)
    }

    override fun ferdigstillAttesteringsoppgave(aktørId: AktørId): Either<KunneIkkeFerdigstilleOppgave, Unit> {
        return oppgaveClient.ferdigstillAttesteringsoppgave(aktørId)
    }

    override fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeFerdigstilleOppgave, Unit> {
        return oppgaveClient.lukkOppgave(oppgaveId)
    }
}
